package net.ai.chatbot.entity.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled or published social media post.
 */
@Document(collection = "social_posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPost {

    @Id
    private String id;

    @Indexed
    private String userId;

    /**
     * Target IDs: accountId:pageId for Facebook, accountId for Twitter
     */
    private List<String> targetIds;

    private String content;

    /**
     * Media items attached to the post
     */
    private List<net.ai.chatbot.dto.social.MediaItem> media;

    /**
     * scheduled | published | pending_publish
     */
    private String status;

    private Instant scheduledAt;
    private Instant publishedAt;

    private Instant createdAt;
}
