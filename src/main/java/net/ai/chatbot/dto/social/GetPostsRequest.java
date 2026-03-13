package net.ai.chatbot.dto.social;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class GetPostsRequest {
    
    @NotNull
    private Instant startDate;
    
    @NotNull
    private Instant endDate;
    
    /**
     * Optional filter by platform: facebook, twitter
     */
    private String platform;
    
    /**
     * Optional filter by status: scheduled, published, pending_publish, etc.
     */
    private String status;
}
