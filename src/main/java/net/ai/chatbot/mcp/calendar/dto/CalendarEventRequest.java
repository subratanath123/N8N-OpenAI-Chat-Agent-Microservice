package net.ai.chatbot.mcp.calendar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a calendar event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventRequest {

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("description")
    private String description;

    @JsonProperty("location")
    private String location;

    @JsonProperty("startDateTime")
    private String startDateTime;

    @JsonProperty("endDateTime")
    private String endDateTime;

    @JsonProperty("timeZone")
    private String timeZone;

    @JsonProperty("attendees")
    private List<String> attendees;

    @JsonProperty("conferenceLink")
    private String conferenceLink;
}

