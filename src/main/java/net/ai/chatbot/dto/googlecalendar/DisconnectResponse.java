package net.ai.chatbot.dto.googlecalendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for disconnecting Google Calendar
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisconnectResponse {

    private boolean success;
    private String message;
    private String chatbotId;
}

