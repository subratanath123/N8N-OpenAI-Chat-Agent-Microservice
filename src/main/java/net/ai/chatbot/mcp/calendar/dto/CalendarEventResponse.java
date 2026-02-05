package net.ai.chatbot.mcp.calendar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for calendar event creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("description")
    private String description;

    @JsonProperty("location")
    private String location;

    @JsonProperty("htmlLink")
    private String htmlLink;

    @JsonProperty("status")
    private String status;

    @JsonProperty("startDateTime")
    private String startDateTime;

    @JsonProperty("endDateTime")
    private String endDateTime;

    @JsonProperty("message")
    private String message;
}

