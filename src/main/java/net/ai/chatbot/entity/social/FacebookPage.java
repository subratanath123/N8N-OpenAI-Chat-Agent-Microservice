package net.ai.chatbot.entity.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Facebook Business Page within a connected account.
 * One Facebook login can contain multiple pages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacebookPage {

    private String pageId;
    private String pageName;
    /**
     * Page access token (stored encrypted)
     */
    private String pageAccessToken;
}
