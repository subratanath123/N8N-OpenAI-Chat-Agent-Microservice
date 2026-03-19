package net.ai.chatbot.dto.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response after storing LinkedIn account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedInConnectResponse {
    
    private boolean success;
    
    /**
     * linkedin
     */
    private String platform;
    
    /**
     * UUID of stored social account
     */
    private String accountId;
}
