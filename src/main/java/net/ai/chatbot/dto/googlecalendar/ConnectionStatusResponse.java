package net.ai.chatbot.dto.googlecalendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Google Calendar connection status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionStatusResponse {

    private boolean connected;
    private String chatbotId;
    private String expiresAt; // ISO 8601 format
    private Boolean isExpired; // null if not connected
}








