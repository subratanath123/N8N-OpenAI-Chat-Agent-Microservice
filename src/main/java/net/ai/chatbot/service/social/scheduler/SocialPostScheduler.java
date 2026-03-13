package net.ai.chatbot.service.social.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.SocialPostDao;
import net.ai.chatbot.entity.social.SocialPost;
import net.ai.chatbot.service.social.publisher.SocialPostPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that publishes due posts to Twitter.
 * 
 * Note: This job ONLY publishes Twitter posts.
 * Facebook posts use native scheduling API and don't need this cron job.
 * 
 * Runs every 60 seconds to check for Twitter posts with scheduledAt <= now.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SocialPostScheduler {

    private final SocialPostDao socialPostDao;
    private final SocialPostPublisher socialPostPublisher;

    /**
     * Poll and publish due Twitter posts every minute.
     * Finds posts with status="scheduled" and scheduledAt <= now.
     * 
     * Facebook posts are skipped - they use native scheduling.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000) // Every 60s, start after 10s
    public void publishDuePosts() {
        Instant now = Instant.now();
        
        List<SocialPost> duePosts = socialPostDao.findByStatusAndScheduledAtBefore("scheduled", now);
        
        if (duePosts.isEmpty()) {
            log.debug("No due posts to publish at {}", now);
            return;
        }

        log.info("Found {} due post(s) to publish", duePosts.size());

        for (SocialPost post : duePosts) {
            try {
                // Publish immediately (Twitter only - Facebook already scheduled natively)
                publishPost(post);
            } catch (Exception e) {
                log.error("Unexpected error publishing post {}: {}", post.getId(), e.getMessage(), e);
            }
        }

        log.info("Completed publishing cycle for {} post(s)", duePosts.size());
    }

    /**
     * Publish a single post immediately and log the result.
     */
    private void publishPost(SocialPost post) {
        log.info("Publishing post {} scheduled for {}", post.getId(), post.getScheduledAt());
        
        // immediate=true because cron job publishes at the scheduled time
        SocialPostPublisher.PublishResult result = socialPostPublisher.publishPost(post, true);

        if (result.allTargetsSucceeded()) {
            log.info("Post {} published successfully to all {} target(s)", 
                    post.getId(), result.successCount());
        } else {
            log.warn("Post {} published with errors: {} succeeded, {} failed",
                    post.getId(), result.successCount(), result.errorCount());
            
            result.errors().forEach(error ->
                    log.warn("  - Target {} failed: {}", error.targetId(), error.errorMessage())
            );
        }
    }
}
