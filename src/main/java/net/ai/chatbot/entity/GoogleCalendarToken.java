package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Entity to store encrypted Google Calendar OAuth tokens for chatbots
 */
@Document(collection = "google_calendar_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleCalendarToken {

    @Id
    private String id;

    /**
     * The chatbot ID this token belongs to (unique)
     */
    @Indexed(unique = true)
    private String chatbotId;

    /**
     * Encrypted access token (stored as JSON string containing IV, encrypted data, and auth tag)
     */
    private String accessToken;

    /**
     * Encrypted refresh token (stored as JSON string containing IV, encrypted data, and auth tag)
     */
    private String refreshToken;

    /**
     * Token expiration timestamp
     */
    @Indexed
    private Date expiresAt;

    /**
     * Token type (usually "Bearer")
     */
    private String tokenType;

    /**
     * When this record was created
     */
    private Date createdAt;

    /**
     * When this record was last updated
     */
    private Date updatedAt;

    /**
     * User who created this token integration
     */
    private String createdBy;
}

