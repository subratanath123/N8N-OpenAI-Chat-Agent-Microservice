package net.ai.chatbot.dto.googlecalendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for storing Google Calendar OAuth tokens
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreTokensRequest {

    @NotBlank(message = "Access token is required")
    private String accessToken;

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    @NotNull(message = "Expires in is required")
    @Positive(message = "Expires in must be positive")
    private Integer expiresIn;

    private String tokenType; // defaults to "Bearer" if not provided
}







