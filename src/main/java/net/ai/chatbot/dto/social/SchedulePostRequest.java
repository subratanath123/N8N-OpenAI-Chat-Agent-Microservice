package net.ai.chatbot.dto.social;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class SchedulePostRequest {

    @NotEmpty
    private List<String> targetIds;

    @NotNull
    private String content;

    /**
     * Media items (uploaded via /v1/api/social-media/upload)
     */
    private List<MediaItem> media;

    /**
     * When to publish (ignored if immediate=true)
     */
    private Instant scheduledAt;

    /**
     * If true, publish immediately
     */
    private boolean immediate;
}
