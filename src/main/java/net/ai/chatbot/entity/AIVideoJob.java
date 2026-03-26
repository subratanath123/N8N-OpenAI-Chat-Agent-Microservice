package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ai_video_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIVideoJob {
    @Id
    private String id;
    
    @Indexed
    private String userEmail;
    
    private String prompt;
    private Integer duration; // in seconds
    private String aspectRatio; // 16:9, 9:16, 1:1
    
    private String status; // pending, processing, completed, failed
    
    private String videoUrl;
    private String thumbnailUrl;
    private String assetId;
    private String error;
    
    @Indexed
    private Instant createdAt;
    private Instant completedAt;
}
