package net.ai.chatbot.dto.googlecalendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for refreshing Google Calendar access token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenResponse {

    private boolean success;
    private String accessToken;
    private Integer expiresIn;
    private String expiresAt; // ISO 8601 format
    private String tokenType;
}

