package net.ai.chatbot.dto.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Media item for social posts (images, videos, documents).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaItem {
    
    private String mediaId;
    private String mediaUrl;
    private String mimeType;
    private String fileName;
    private Long sizeBytes;
    
    // Optional fields for images/videos
    private Integer width;
    private Integer height;
    private Long durationMs;
    private String thumbnailUrl;
}
