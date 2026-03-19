package net.ai.chatbot.service.social.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.SocialPostDao;
import net.ai.chatbot.dto.social.TokenResolutionResponse;
import net.ai.chatbot.entity.social.SocialPost;
import net.ai.chatbot.service.social.SocialAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates publishing posts to social platforms.
 * 
 * Strategy:
 * - Facebook: Uses native scheduling API (no cron job needed)
 * - Twitter: Uses cron job (no native scheduling in public API)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SocialPostPublisher {

    private final SocialPostDao socialPostDao;
    private final SocialAccountService socialAccountService;
    private final FacebookPublisher facebookPublisher;
    private final TwitterPublisher twitterPublisher;
    private final LinkedInPublisher linkedInPublisher;

    /**
     * Publish a post to all its targets.
     * - Facebook: Uses native scheduling (immediate or scheduled)
     * - Twitter: Publishes immediately (scheduled posts handled by cron)
     *
     * @param post     The post to publish
     * @param immediate If true, publish immediately; if false, use platform scheduling
     * @return PublishResult with success status and errors
     */
    @Transactional
    public PublishResult publishPost(SocialPost post, boolean immediate) {
        log.info("Publishing post {} to {} target(s) (immediate={})", 
                post.getId(), post.getTargetIds().size(), immediate);

        List<String> successTargets = new ArrayList<>();
        List<TargetError> errors = new ArrayList<>();

        for (String targetId : post.getTargetIds()) {
            try {
                publishToTarget(post, targetId, immediate);
                successTargets.add(targetId);
                log.info("Successfully published post {} to target {}", post.getId(), targetId);
                
            } catch (Exception e) {
                log.error("Failed to publish post {} to target {}: {}", 
                        post.getId(), targetId, e.getMessage(), e);
                errors.add(new TargetError(targetId, e.getMessage()));
            }
        }

        // Update post status if at least one target succeeded
        if (!successTargets.isEmpty()) {
            post.setStatus("published");
            post.setPublishedAt(Instant.now());
            socialPostDao.save(post);
            log.info("Post {} marked as published", post.getId());
        }

        boolean allSuccess = errors.isEmpty();
        return new PublishResult(
                post.getId(),
                allSuccess,
                successTargets.size(),
                errors.size(),
                errors
        );
    }

    /**
     * Publish post content to a specific target.
     */
    private void publishToTarget(SocialPost post, String targetId, boolean immediate) {
        // Resolve token for target
        TokenResolutionResponse token = socialAccountService.resolveToken(post.getUserId(), targetId);

        // Publish based on platform
        switch (token.getPlatform()) {
            case "facebook" -> {
                if (immediate) {
                    facebookPublisher.publishImmediately(
                            token.getPageId(),
                            token.getPageAccessToken(),
                            post.getContent(),
                            post.getMedia(),
                            post.getUserId()
                    );
                } else {
                    // Facebook native scheduling
                    facebookPublisher.publishScheduled(
                            token.getPageId(),
                            token.getPageAccessToken(),
                            post.getContent(),
                            post.getMedia(),
                            post.getUserId(),
                            post.getScheduledAt()
                    );
                }
            }
            case "twitter" -> {
                // Twitter has no native scheduling in public API
                // Always publish immediately (cron job handles scheduling)
                twitterPublisher.publishImmediately(
                        token.getAccessToken(),
                        token.getUsername(),
                        post.getContent(),
                        post.getMedia(),
                        post.getUserId()
                );
            }
            case "linkedin" -> {
                // LinkedIn has no native scheduling in basic products
                // Always publish immediately (cron job handles scheduling)
                linkedInPublisher.publishImmediately(
                        token.getAccessToken(),
                        token.getLinkedInUserId(),
                        token.getDisplayName(),
                        post.getContent(),
                        post.getMedia(),
                        post.getUserId()
                );
            }
            default -> throw new IllegalStateException("Unknown platform: " + token.getPlatform());
        }
    }

    /**
     * Result of publishing a post.
     */
    public record PublishResult(
            String postId,
            boolean allTargetsSucceeded,
            int successCount,
            int errorCount,
            List<TargetError> errors
    ) {}

    /**
     * Error for a specific target.
     */
    public record TargetError(
            String targetId,
            String errorMessage
    ) {}
}
