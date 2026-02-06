package net.ai.chatbot.mcp.calendar.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.mcp.calendar.tools.CalendarEventTool;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST Controller to expose MCP tools as HTTP endpoints for n8n integration.
 * 
 * This controller supports both:
 * 1. REST-style endpoints (GET /mcp/health, POST /mcp/tools/list, POST /mcp/tools/invoke)
 * 2. JSON-RPC 2.0 format (POST /mcp with method field)
 * 
 * n8n's MCP Client Tool uses JSON-RPC 2.0 format.
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpRestController {

    private final CalendarEventTool calendarEventTool;

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
        
        Map<String, Object> result = Map.of(
            "tools", List.of(
                Map.of(
                    "name", "create_calendar_event",
                    "description", "Create a new event in Google Calendar. Requires a Google Calendar OAuth2 access token.",
                    "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "accessToken", Map.of(
                                "type", "string",
                                "description", "Google Calendar OAuth2 access token"
                            ),
                            "summary", Map.of(
                                "type", "string",
                                "description", "Event title/summary"
                            ),
                            "description", Map.of(
                                "type", "string",
                                "description", "Event description"
                            ),
                            "startDateTime", Map.of(
                                "type", "string",
                                "description", "Event start date/time in ISO 8601 format (e.g., 2024-01-15T10:00:00)"
                            ),
                            "endDateTime", Map.of(
                                "type", "string",
                                "description", "Event end date/time in ISO 8601 format (e.g., 2024-01-15T11:00:00)"
                            ),
                            "timeZone", Map.of(
                                "type", "string",
                                "description", "Time zone (e.g., UTC, America/New_York). Defaults to UTC if not provided"
                            ),
                            "location", Map.of(
                                "type", "string",
                                "description", "Event location"
                            ),
                            "attendees", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "List of attendee email addresses"
                            )
                        ),
                        "required", List.of("accessToken", "summary", "startDateTime", "endDateTime")
                    )
                )
            )
        );
        
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
        String accessToken = (String) arguments.get("accessToken");
        String summary = (String) arguments.get("summary");
        String description = (String) arguments.get("description");
        String startDateTime = (String) arguments.get("startDateTime");
        String endDateTime = (String) arguments.get("endDateTime");
        String timeZone = (String) arguments.get("timeZone");
        String location = (String) arguments.get("location");
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) arguments.get("attendees");
        
        // Validate required arguments
        if (accessToken == null || accessToken.isBlank()) {
            return Mono.just(ResponseEntity.ok(
                createJsonRpcError(id, -32602, "Missing required argument: accessToken", null)
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
        
        // Call the MCP tool
        return calendarEventTool.createCalendarEvent(
                accessToken, summary, description, startDateTime, endDateTime, 
                timeZone, location, attendees
            )
            .map(response -> ResponseEntity.ok(createJsonRpcSuccess(id, Map.of(
                "content", List.of(
                    Map.of(
                        "type", "text",
                        "text", String.format("Calendar event created successfully!\n\nEvent ID: %s\nSummary: %s\nLink: %s\nStatus: %s",
                            response.getId(), response.getSummary(), response.getHtmlLink(), response.getStatus())
                    )
                ),
                "isError", false
            ))))
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
     * List all available MCP tools
     * 
     * GET /mcp/tools/list
     */
    @PostMapping("/tools/list")
    public ResponseEntity<Map<String, Object>> listTools() {
        log.info("MCP REST: List tools requested");
        
        Map<String, Object> response = Map.of(
            "tools", List.of(
                Map.of(
                    "name", "create_calendar_event",
                    "description", "Create a new event in Google Calendar. Requires a Google Calendar OAuth2 access token.",
                    "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "accessToken", Map.of(
                                "type", "string",
                                "description", "Google Calendar OAuth2 access token"
                            ),
                            "summary", Map.of(
                                "type", "string",
                                "description", "Event title/summary"
                            ),
                            "description", Map.of(
                                "type", "string",
                                "description", "Event description"
                            ),
                            "startDateTime", Map.of(
                                "type", "string",
                                "description", "Event start date/time in ISO 8601 format (e.g., 2024-01-15T10:00:00)"
                            ),
                            "endDateTime", Map.of(
                                "type", "string",
                                "description", "Event end date/time in ISO 8601 format (e.g., 2024-01-15T11:00:00)"
                            ),
                            "timeZone", Map.of(
                                "type", "string",
                                "description", "Time zone (e.g., UTC, America/New_York). Defaults to UTC if not provided"
                            ),
                            "location", Map.of(
                                "type", "string",
                                "description", "Event location"
                            ),
                            "attendees", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "List of attendee email addresses"
                            )
                        ),
                        "required", List.of("accessToken", "summary", "startDateTime", "endDateTime")
                    )
                )
            )
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Invoke an MCP tool
     * 
     * POST /mcp/tools/invoke
     * 
     * Request body:
     * {
     *   "name": "create_calendar_event",
     *   "arguments": {
     *     "accessToken": "...",
     *     "summary": "Meeting",
     *     "startDateTime": "2024-01-15T10:00:00",
     *     "endDateTime": "2024-01-15T11:00:00",
     *     "timeZone": "UTC",
     *     "location": "Conference Room",
     *     "attendees": ["user@example.com"]
     *   }
     * }
     */
    @PostMapping("/tools/invoke")
    public Mono<ResponseEntity<Map<String, Object>>> invokeTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");
        
        log.info("MCP REST: Invoke tool '{}' with arguments: {}", toolName, arguments);
        
        if (!"create_calendar_event".equals(toolName)) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Unknown tool: " + toolName,
                    "availableTools", List.of("create_calendar_event")
                )));
        }
        
        // Extract arguments
        String accessToken = (String) arguments.get("accessToken");
        String summary = (String) arguments.get("summary");
        String description = (String) arguments.get("description");
        String startDateTime = (String) arguments.get("startDateTime");
        String endDateTime = (String) arguments.get("endDateTime");
        String timeZone = (String) arguments.get("timeZone");
        String location = (String) arguments.get("location");
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) arguments.get("attendees");
        
        // Validate required arguments
        if (accessToken == null || accessToken.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("error", "Missing required argument: accessToken")));
        }
        if (summary == null || summary.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("error", "Missing required argument: summary")));
        }
        if (startDateTime == null || startDateTime.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("error", "Missing required argument: startDateTime")));
        }
        if (endDateTime == null || endDateTime.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("error", "Missing required argument: endDateTime")));
        }
        
        // Call the MCP tool
        return calendarEventTool.createCalendarEvent(
                accessToken,
                summary,
                description,
                startDateTime,
                endDateTime,
                timeZone,
                location,
                attendees
            )
            .map(response -> ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "id", response.getId(),
                    "htmlLink", response.getHtmlLink(),
                    "status", response.getStatus(),
                    "summary", response.getSummary(),
                    "startDateTime", response.getStartDateTime(),
                    "endDateTime", response.getEndDateTime()
                )
            )))
            .onErrorResume(error -> {
                log.error("Error invoking tool '{}': {}", toolName, error.getMessage(), error);
                return Mono.just(ResponseEntity.status(500)
                    .body(Map.of(
                        "success", false,
                        "error", error.getMessage() != null ? error.getMessage() : "Unknown error"
                    )));
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

