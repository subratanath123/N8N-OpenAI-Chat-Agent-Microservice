package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ai_product_studio_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIProductStudioJob {
    @Id
    private String id;
    
    @Indexed
    private String userEmail;
    
    private String originalImageUrl;
    private String sceneType; // studio, outdoor, lifestyle, luxury
    private String lighting; // soft, dramatic, natural, neon
    private String angle; // front, angle, top, 360
    
    private String status; // pending, processing, completed, failed
    
    private String resultUrl;
    private String assetId;
    private String error;
    
    @Indexed
    private Instant createdAt;
    private Instant completedAt;
}
