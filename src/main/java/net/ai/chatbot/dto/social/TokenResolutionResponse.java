package net.ai.chatbot.dto.social;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResolutionResponse {
    private String platform;
    private String pageAccessToken;  // Facebook
    private String pageId;           // Facebook
    private String accessToken;      // Twitter
    private String username;         // Twitter
}
