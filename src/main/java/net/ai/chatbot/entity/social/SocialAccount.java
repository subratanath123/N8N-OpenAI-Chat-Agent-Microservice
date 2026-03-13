package net.ai.chatbot.entity.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

/**
 * Connected social media account (Facebook or Twitter).
 * Stored per userId - users can connect multiple accounts per platform.
 */
@Document(collection = "social_accounts")
@CompoundIndex(name = "userId_platform", def = "{'userId': 1, 'platform': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialAccount {

    @Id
    private String id;

    /**
     * Clerk user ID (JWT sub)
     */
    private String userId;

    /**
     * facebook | twitter
     */
    private String platform;

    private Date connectedAt;

    // --- Facebook fields ---
    /**
     * Long-lived user access token (encrypted)
     */
    private String longLivedToken;
    private Long expiresIn;

    /**
     * Facebook pages - one login can have multiple pages
     */
    private List<FacebookPage> pages;

    // --- Twitter fields ---
    private String accessToken;   // encrypted
    private String refreshToken; // encrypted
    private String username;
}
