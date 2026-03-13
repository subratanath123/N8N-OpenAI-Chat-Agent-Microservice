package net.ai.chatbot.dto.social;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class SocialPostResponse {
    
    private String postId;
    private String userId;
    private List<String> targetIds;
    private String content;
    private List<MediaItem> media;
    private String status;
    private Instant scheduledAt;
    private Instant publishedAt;
    private Instant createdAt;
    
    /**
     * Display info for targets (for frontend)
     */
    private List<TargetInfo> targets;
    
    @Data
    @Builder
    public static class TargetInfo {
        private String targetId;
        private String platform;
        private String displayName;
    }
}
