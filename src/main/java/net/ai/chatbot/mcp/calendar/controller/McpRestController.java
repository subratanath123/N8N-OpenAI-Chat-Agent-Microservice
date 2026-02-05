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
 * This controller wraps the @McpTool annotated methods and exposes them as
 * standard REST endpoints that n8n can easily call.
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpRestController {

    private final CalendarEventTool calendarEventTool;

    /**
     * List all available MCP tools
     * 
     * GET /mcp/tools/list
     */
    @GetMapping("/tools/list")
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

