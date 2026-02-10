package net.ai.chatbot.dto.googlecalendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for storing Google Calendar OAuth tokens
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreTokensResponse {

    private boolean success;
    private String message;
    private String chatbotId;
}







