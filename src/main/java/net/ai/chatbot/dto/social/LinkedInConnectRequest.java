package net.ai.chatbot.dto.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to store LinkedIn account connection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedInConnectRequest {
    
    /**
     * OAuth 2.0 access token - use this when posting
     */
    private String accessToken;
    
    /**
     * Refresh token (null for basic LinkedIn products)
     * Only issued with offline_access scope in Marketing Developer Platform
     */
    private String refreshToken;
    
    /**
     * Access token expiry in seconds (~60 days for Sign In with LinkedIn)
     */
    private Long expiresIn;
    
    /**
     * LinkedIn user ID (OpenID Connect sub claim)
     * Format: urn:li:person:AbCdEfGhIj
     */
    private String linkedInUserId;
    
    /**
     * Full name for display in UI
     */
    private String displayName;
    
    /**
     * User's LinkedIn email
     */
    private String email;
    
    /**
     * Profile picture URL
     */
    private String profilePicture;
}
