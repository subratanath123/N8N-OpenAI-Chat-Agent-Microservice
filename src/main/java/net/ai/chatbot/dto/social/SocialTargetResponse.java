package net.ai.chatbot.dto.social;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SocialTargetResponse {

    /**
     * Facebook: accountId:pageId | Twitter: accountId
     */
    private String targetId;
    private String accountId;
    private String platform;
    private String displayName;
    private String pageId;   // Facebook
    private String pageName; // Facebook
    private String username; // Twitter
}
