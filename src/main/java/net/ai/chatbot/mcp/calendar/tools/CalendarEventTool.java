package net.ai.chatbot.mcp.calendar.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.mcp.calendar.dto.CalendarEventRequest;
import net.ai.chatbot.mcp.calendar.dto.CalendarEventResponse;
import net.ai.chatbot.mcp.calendar.service.GoogleCalendarService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Note: Uncomment @McpTool and @McpToolParam annotations after rebuilding the project
 * to resolve spring-ai-starter-mcp-server dependency.
 * <p>
 * The annotations should be:
 * import org.springframework.ai.mcp.server.annotation.McpTool;
 * import org.springframework.ai.mcp.server.annotation.McpToolParam;
 */

/**
 * MCP Tool for calendar event creation
 * <p>
 * This tool exposes calendar event creation functionality through the MCP protocol.
 * MCP clients (like n8n, Claude Desktop, etc.) can discover and invoke this tool.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarEventTool {

    private final GoogleCalendarService googleCalendarService;

    /**
     * Create a calendar event in Google Calendar
     * <p>
     * This MCP tool allows creating calendar events with the following features:
     * - Event title (summary)
     * - Description
     * - Location
     * - Start and end date/time
     * - Time zone support
     * - Multiple attendees
     * <p>
     * TODO: Uncomment @McpTool and @McpToolParam annotations after dependency resolution:
     *
     * @param accessToken   Google Calendar OAuth2 access token (@McpToolParam(required = true))
     * @param summary       Event title (@McpToolParam(required = true))
     * @param description   Event description (@McpToolParam)
     * @param startDateTime Event start time in ISO 8601 format (@McpToolParam(required = true))
     * @param endDateTime   Event end time in ISO 8601 format (@McpToolParam(required = true))
     * @param timeZone      Time zone (defaults to UTC) (@McpToolParam)
     * @param location      Event location (@McpToolParam)
     * @param attendees     List of attendee email addresses (@McpToolParam)
     * @return Created calendar event response
     * @McpTool( name = "create_calendar_event",
     * description = "Create a new event in Google Calendar. Requires a Google Calendar OAuth2 access token."
     * )
     */
    @McpTool(name = "create_calendar_event", description = "Create a new event in Google Calendar...")
    public Mono<CalendarEventResponse> createCalendarEvent(
            @McpToolParam(description = "Google Calendar OAuth2 access token", required = true)
            String accessToken,

            @McpToolParam(description = "Event title/summary", required = true)
            String summary,
            @McpToolParam(description = "Event description")
            String description,

            @McpToolParam(description = "Event start date/time in ISO 8601 format", required = true)
            String startDateTime,

            @McpToolParam(description = "Event end date/time in ISO 8601 format", required = true)
            String endDateTime,

            @McpToolParam(description = "Time zone. Defaults to UTC if not provided")
            String timeZone,

            @McpToolParam(description = "Event location")
            String location,

            @McpToolParam(description = "List of attendee email addresses")
            List<String> attendees) {

        log.info("MCP Tool invoked: create_calendar_event for event '{}'", summary);

        CalendarEventRequest request = CalendarEventRequest.builder()
                .summary(summary)
                .description(description)
                .location(location)
                .startDateTime(startDateTime)
                .endDateTime(endDateTime)
                .timeZone(timeZone != null ? timeZone : "UTC")
                .attendees(attendees)
                .build();

        return googleCalendarService.createEvent(accessToken, request)
                .doOnSuccess(response -> log.info("Calendar event created via MCP: {}", response.getId()))
                .doOnError(error -> log.error("Failed to create calendar event via MCP: {}", error.getMessage()));
    }
}

