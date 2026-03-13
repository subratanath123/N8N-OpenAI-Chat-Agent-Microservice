package net.ai.chatbot.dto.social;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SchedulePostResponse {
    private boolean success;
    private String postId;
    private String status; // Made mutable for controller updates
    private Instant scheduledAt;
}
