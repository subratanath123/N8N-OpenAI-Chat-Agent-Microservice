package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ai_product_photo_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIProductPhotoJob {
    @Id
    private String id;
    
    @Indexed
    private String userEmail;
    
    private String originalImageUrl;
    private String backgroundType; // white, gradient, custom, lifestyle
    private String customPrompt;
    
    private String status; // pending, processing, completed, failed
    
    private String resultUrl;
    private String assetId;
    private String error;
    
    @Indexed
    private Instant createdAt;
    private Instant completedAt;
}
