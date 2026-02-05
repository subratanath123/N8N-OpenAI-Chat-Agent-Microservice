package net.ai.chatbot.mcp.calendar.service;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.mcp.calendar.dto.CalendarEventRequest;
import net.ai.chatbot.mcp.calendar.dto.CalendarEventResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for interacting with Google Calendar API
 */
@Slf4j
@Service
public class GoogleCalendarService {

    private static final String GOOGLE_CALENDAR_API_BASE_URL = "https://www.googleapis.com/calendar/v3";
    private static final String CALENDAR_ID = "primary";

    private final WebClient webClient;

    public GoogleCalendarService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(GOOGLE_CALENDAR_API_BASE_URL)
                .build();
    }

    /**
     * Create a calendar event
     * 
     * @param accessToken Google Calendar OAuth2 access token
     * @param request Calendar event details
     * @return Created calendar event response
     */
    public Mono<CalendarEventResponse> createEvent(String accessToken, CalendarEventRequest request) {
        log.info("Creating calendar event: {}", request.getSummary());

        String url = String.format("/calendars/%s/events", CALENDAR_ID);
        Map<String, Object> requestBody = buildGoogleCalendarRequestBody(request);

        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::mapToCalendarEventResponse)
                .doOnSuccess(response -> log.info("Calendar event created successfully: {}", response.getId()))
                .doOnError(error -> log.error("Error creating calendar event: {}", error.getMessage()));
    }

    /**
     * Build Google Calendar API request body
     */
    private Map<String, Object> buildGoogleCalendarRequestBody(CalendarEventRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("summary", request.getSummary());

        if (request.getDescription() != null) {
            body.put("description", request.getDescription());
        }

        if (request.getLocation() != null) {
            body.put("location", request.getLocation());
        }

        // Start time
        Map<String, String> start = new HashMap<>();
        start.put("dateTime", request.getStartDateTime());
        start.put("timeZone", request.getTimeZone() != null ? request.getTimeZone() : "UTC");
        body.put("start", start);

        // End time
        Map<String, String> end = new HashMap<>();
        end.put("dateTime", request.getEndDateTime());
        end.put("timeZone", request.getTimeZone() != null ? request.getTimeZone() : "UTC");
        body.put("end", end);

        // Attendees
        if (request.getAttendees() != null && !request.getAttendees().isEmpty()) {
            List<Map<String, String>> attendees = request.getAttendees().stream()
                    .map(email -> Map.of("email", email))
                    .collect(Collectors.toList());
            body.put("attendees", attendees);
        }

        return body;
    }

    /**
     * Map Google Calendar API response to our DTO
     */
    @SuppressWarnings("unchecked")
    private CalendarEventResponse mapToCalendarEventResponse(Map<String, Object> googleResponse) {
        Map<String, Object> start = (Map<String, Object>) googleResponse.get("start");
        Map<String, Object> end = (Map<String, Object>) googleResponse.get("end");

        return CalendarEventResponse.builder()
                .id((String) googleResponse.get("id"))
                .summary((String) googleResponse.get("summary"))
                .description((String) googleResponse.get("description"))
                .location((String) googleResponse.get("location"))
                .htmlLink((String) googleResponse.get("htmlLink"))
                .status((String) googleResponse.get("status"))
                .startDateTime(start != null ? (String) start.get("dateTime") : null)
                .endDateTime(end != null ? (String) end.get("dateTime") : null)
                .message("Calendar event created successfully")
                .build();
    }
}

