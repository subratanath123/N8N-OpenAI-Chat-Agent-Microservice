package net.ai.chatbot.dto.social;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class SocialAccountResponse {

    private String accountId;
    private String platform;
    private Instant connectedAt;
    private List<PageInfo> pages;  // Facebook
    private String username;       // Twitter

    @Data
    @Builder
    public static class PageInfo {
        private String pageId;
        private String pageName;
    }
}
