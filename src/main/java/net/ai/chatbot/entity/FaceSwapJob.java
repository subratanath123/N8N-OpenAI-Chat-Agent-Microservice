package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "face_swap_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceSwapJob {
    @Id
    private String id;
    
    @Indexed
    private String userEmail;
    
    private String sourceImageUrl;
    private String targetImageUrl;
    
    private String status; // pending, processing, completed, failed
    
    private String resultUrl;
    private String assetId;
    private String error;
    
    @Indexed
    private Instant createdAt;
    private Instant completedAt;
}
