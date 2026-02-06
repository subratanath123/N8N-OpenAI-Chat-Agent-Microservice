package net.ai.chatbot.mcp.calendar.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.GoogleCalendarTokenDao;
import net.ai.chatbot.entity.GoogleCalendarToken;
import net.ai.chatbot.mcp.calendar.tools.CalendarEventTool;
import net.ai.chatbot.service.googlecalendar.GoogleOAuthService;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * REST Controller to expose MCP tools as HTTP endpoints for n8n integration.
 * 
 * This controller implements the MCP (Model Context Protocol) JSON-RPC 2.0 specification.
 * n8n's MCP Client Tool communicates with this server using JSON-RPC 2.0 format.
 * 
 * Main endpoint: POST /mcp (JSON-RPC 2.0)
 * Health check: GET /mcp/health
 * 
 * The controller automatically fetches stored Google Calendar OAuth tokens from the database
 * using the chatbotId parameter, with automatic token refresh if expired.
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpRestController {

    private final CalendarEventTool calendarEventTool;
    private final GoogleCalendarTokenDao tokenDao;
    private final EncryptionUtils encryptionUtils;
    private final GoogleOAuthService oauthService;

    /**
     * JSON-RPC 2.0 endpoint for MCP protocol
     * 
     * POST /mcp
     * 
     * This is the main entry point for MCP clients (like n8n) that use JSON-RPC 2.0.
     * 
     * Request format:
     * {
     *   "jsonrpc": "2.0",
     *   "method": "tools/list" | "tools/call",
     *   "params": { ... },
     *   "id": 1
     * }
     * 
     * Response format:
     * {
     *   "jsonrpc": "2.0",
     *   "result": { ... },
     *   "id": 1
     * }
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> handleJsonRpc(@RequestBody Map<String, Object> request) {
        String jsonrpc = (String) request.get("jsonrpc");
        String method = (String) request.get("method");
        Object id = request.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());
        
        log.info("MCP JSON-RPC: method='{}', id={}", method, id);
        
        // Validate JSON-RPC 2.0 format
        if (!"2.0".equals(jsonrpc)) {
            return Mono.just(ResponseEntity.badRequest()
                .body(createJsonRpcError(id, -32600, "Invalid Request: jsonrpc must be '2.0'", null)));
        }
        
        if (method == null || method.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(createJsonRpcError(id, -32600, "Invalid Request: method is required", null)));
        }
        
        // Route to appropriate handler
        return switch (method) {
            case "tools/list", "tools.list" -> handleToolsList(id);
            case "tools/call", "tools.call" -> handleToolsCall(id, params);
            case "initialize" -> handleInitialize(id, params);
            default -> Mono.just(ResponseEntity.ok(
                createJsonRpcError(id, -32601, "Method not found: " + method, null)
            ));
        };
    }
    
    /**
     * Handle tools/list method (JSON-RPC)
     */
    private Mono<ResponseEntity<Map<String, Object>>> handleToolsList(Object id) {
        log.info("MCP JSON-RPC: Handling tools/list");
        
        // Build tool schema according to MCP specification
        // Each property needs: type, description, and inputType for n8n compatibility
        Map<String, Object> chatbotIdProp = new java.util.HashMap<>();
        chatbotIdProp.put("type", "string");
        chatbotIdProp.put("description", "Chatbot ID to fetch the stored Google Calendar token");
        chatbotIdProp.put("inputType", "string");
        
        Map<String, Object> summaryProp = new java.util.HashMap<>();
        summaryProp.put("type", "string");
        summaryProp.put("description", "Event title/summary");
        summaryProp.put("inputType", "string");
        
        Map<String, Object> descriptionProp = new java.util.HashMap<>();
        descriptionProp.put("type", "string");
        descriptionProp.put("description", "Event description");
        descriptionProp.put("inputType", "string");
        
        Map<String, Object> startDateTimeProp = new java.util.HashMap<>();
        startDateTimeProp.put("type", "string");
        startDateTimeProp.put("description", "Event start date/time in ISO 8601 format (e.g., 2024-01-15T10:00:00)");
        startDateTimeProp.put("inputType", "string");
        
        Map<String, Object> endDateTimeProp = new java.util.HashMap<>();
        endDateTimeProp.put("type", "string");
        endDateTimeProp.put("description", "Event end date/time in ISO 8601 format (e.g., 2024-01-15T11:00:00)");
        endDateTimeProp.put("inputType", "string");
        
        Map<String, Object> timeZoneProp = new java.util.HashMap<>();
        timeZoneProp.put("type", "string");
        timeZoneProp.put("description", "Time zone (e.g., UTC, America/New_York). Defaults to UTC if not provided");
        timeZoneProp.put("inputType", "string");
        
        Map<String, Object> locationProp = new java.util.HashMap<>();
        locationProp.put("type", "string");
        locationProp.put("description", "Event location");
        locationProp.put("inputType", "string");
        
        Map<String, Object> attendeesItemsProp = new java.util.HashMap<>();
        attendeesItemsProp.put("type", "string");
        
        Map<String, Object> attendeesProp = new java.util.HashMap<>();
        attendeesProp.put("type", "array");
        attendeesProp.put("items", attendeesItemsProp);
        attendeesProp.put("description", "List of attendee email addresses");
        attendeesProp.put("inputType", "array");
        
        Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("chatbotId", chatbotIdProp);
        properties.put("summary", summaryProp);
        properties.put("description", descriptionProp);
        properties.put("startDateTime", startDateTimeProp);
        properties.put("endDateTime", endDateTimeProp);
        properties.put("timeZone", timeZoneProp);
        properties.put("location", locationProp);
        properties.put("attendees", attendeesProp);
        
        Map<String, Object> inputSchema = new java.util.HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("chatbotId", "summary", "startDateTime", "endDateTime"));
        
        Map<String, Object> tool = new java.util.HashMap<>();
        tool.put("name", "create_calendar_event");
        tool.put("description", "Create a new event in Google Calendar with auto-generated Google Meet link. Uses stored OAuth token for the chatbot.");
        tool.put("inputSchema", inputSchema);
        
        Map<String, Object> result = Map.of("tools", List.of(tool));
        
        return Mono.just(ResponseEntity.ok(createJsonRpcSuccess(id, result)));
    }
    
    /**
     * Handle tools/call method (JSON-RPC)
     */
    private Mono<ResponseEntity<Map<String, Object>>> handleToolsCall(Object id, Map<String, Object> params) {
        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
        
        log.info("MCP JSON-RPC: Calling tool '{}' with arguments: {}", toolName, arguments);
        
        if (!"create_calendar_event".equals(toolName)) {
            return Mono.just(ResponseEntity.ok(
                createJsonRpcError(id, -32602, "Unknown tool: " + toolName, 
                    Map.of("availableTools", List.of("create_calendar_event")))
            ));
        }
        
        // Extract and validate arguments
        String chatbotId = (String) arguments.get("chatbotId");
        String summary = (String) arguments.get("summary");
        String description = (String) arguments.get("description");
        String startDateTime = (String) arguments.get("startDateTime");
        String endDateTime = (String) arguments.get("endDateTime");
        String timeZone = (String) arguments.get("timeZone");
        String location = (String) arguments.get("location");
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) arguments.get("attendees");
        
        // Validate required arguments
        if (chatbotId == null || chatbotId.isBlank()) {
            return Mono.just(ResponseEntity.ok(
                createJsonRpcError(id, -32602, "Missing required argument: chatbotId", null)
            ));
        }
        if (summary == null || summary.isBlank()) {
            return Mono.just(ResponseEntity.ok(
                createJsonRpcError(id, -32602, "Missing required argument: summary", null)
            ));
        }
        if (startDateTime == null || startDateTime.isBlank()) {
            return Mono.just(ResponseEntity.ok(
                createJsonRpcError(id, -32602, "Missing required argument: startDateTime", null)
            ));
        }
        if (endDateTime == null || endDateTime.isBlank()) {
            return Mono.just(ResponseEntity.ok(
                createJsonRpcError(id, -32602, "Missing required argument: endDateTime", null)
            ));
        }
        
        // Fetch and decrypt access token from database
        return getValidAccessToken(chatbotId)
            .flatMap(accessToken -> 
                // Call the MCP tool with fetched access token
                // Google Meet link will be auto-generated
                calendarEventTool.createCalendarEvent(
                    accessToken, summary, description, startDateTime, endDateTime, 
                    timeZone, location, attendees
                )
            )
            .map(response -> {
                StringBuilder responseText = new StringBuilder();
                responseText.append("Calendar event created successfully!\n\n");
                responseText.append("Event ID: ").append(response.getId()).append("\n");
                responseText.append("Summary: ").append(response.getSummary()).append("\n");
                responseText.append("Link: ").append(response.getHtmlLink()).append("\n");
                responseText.append("Status: ").append(response.getStatus());
                
                if (response.getConferenceLink() != null && !response.getConferenceLink().isBlank()) {
                    responseText.append("\n📞 Conference Link: ").append(response.getConferenceLink());
                }
                
                return ResponseEntity.ok(createJsonRpcSuccess(id, Map.of(
                    "content", List.of(
                        Map.of(
                            "type", "text",
                            "text", responseText.toString()
                        )
                    ),
                    "isError", false
                )));
            })
            .onErrorResume(error -> {
                log.error("Error calling tool '{}': {}", toolName, error.getMessage(), error);
                return Mono.just(ResponseEntity.ok(
                    createJsonRpcError(id, -32603, "Internal error: " + error.getMessage(), null)
                ));
            });
    }
    
    /**
     * Handle initialize method (JSON-RPC)
     */
    private Mono<ResponseEntity<Map<String, Object>>> handleInitialize(Object id, Map<String, Object> params) {
        log.info("MCP JSON-RPC: Handling initialize");
        
        Map<String, Object> result = Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(
                "tools", Map.of("listChanged", false)
            ),
            "serverInfo", Map.of(
                "name", "Calendar MCP Server",
                "version", "1.0.0"
            )
        );
        
        return Mono.just(ResponseEntity.ok(createJsonRpcSuccess(id, result)));
    }
    
    /**
     * Create JSON-RPC 2.0 success response
     */
    private Map<String, Object> createJsonRpcSuccess(Object id, Map<String, Object> result) {
        return Map.of(
            "jsonrpc", "2.0",
            "result", result,
            "id", id != null ? id : 0
        );
    }
    
    /**
     * Create JSON-RPC 2.0 error response
     */
    private Map<String, Object> createJsonRpcError(Object id, int code, String message, Object data) {
        Map<String, Object> error = data != null 
            ? Map.of("code", code, "message", message, "data", data)
            : Map.of("code", code, "message", message);
            
        return Map.of(
            "jsonrpc", "2.0",
            "error", error,
            "id", id != null ? id : 0
        );
    }

    /**
     * Get valid access token for a chatbot, automatically refreshing if expired
     * 
     * @param chatbotId The chatbot ID
     * @return Mono with valid access token
     */
    private Mono<String> getValidAccessToken(String chatbotId) {
        log.info("Fetching access token for chatbot: {}", chatbotId);
        
        return Mono.fromCallable(() -> {
            // Get token from database
            GoogleCalendarToken token = tokenDao.findByChatbotId(chatbotId)
                .orElseThrow(() -> new RuntimeException(
                    "Google Calendar not connected for chatbot: " + chatbotId + 
                    ". Please connect Google Calendar first."));
            
            // Check if token is expired
            if (token.getExpiresAt().before(new Date())) {
                log.info("Access token expired for chatbot {}, will refresh", chatbotId);
                return token; // Return for refresh
            } else {
                // Token is still valid, decrypt and return
                String decryptedToken = encryptionUtils.decrypt(token.getAccessToken());
                log.info("Using valid cached token for chatbot: {}", chatbotId);
                return decryptedToken;
            }
        })
        .flatMap(result -> {
            if (result instanceof GoogleCalendarToken) {
                // Token was expired, refresh it
                GoogleCalendarToken token = (GoogleCalendarToken) result;
                String decryptedRefreshToken = encryptionUtils.decrypt(token.getRefreshToken());
                
                return oauthService.refreshAccessToken(decryptedRefreshToken)
                    .map(newTokens -> {
                        // Update token in database
                        Date newExpiresAt = new Date(System.currentTimeMillis() + newTokens.expiresIn * 1000L);
                        String encryptedAccessToken = encryptionUtils.encrypt(newTokens.accessToken);
                        
                        token.setAccessToken(encryptedAccessToken);
                        token.setExpiresAt(newExpiresAt);
                        token.setTokenType(newTokens.tokenType);
                        token.setUpdatedAt(new Date());
                        tokenDao.save(token);
                        
                        log.info("Successfully refreshed token for chatbot: {}", chatbotId);
                        return newTokens.accessToken;
                    })
                    .onErrorMap(e -> new RuntimeException(
                        "Failed to refresh Google Calendar token for chatbot: " + chatbotId + 
                        ". Error: " + e.getMessage() + 
                        ". Please reconnect Google Calendar.", e));
            } else {
                // Token was valid, return it
                return Mono.just((String) result);
            }
        })
        .onErrorMap(e -> {
            if (e instanceof RuntimeException) {
                return e;
            }
            return new RuntimeException("Error fetching access token: " + e.getMessage(), e);
        });
    }

    /**
     * Health check endpoint for MCP server
     * 
     * GET /mcp/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Calendar MCP Server",
            "availableTools", List.of("create_calendar_event")
        ));
    }
}

