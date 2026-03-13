package net.ai.chatbot.dao;

import net.ai.chatbot.entity.social.SocialPost;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SocialPostDao extends MongoRepository<SocialPost, String> {

    List<SocialPost> findByUserId(String userId);

    List<SocialPost> findByStatusAndScheduledAtBefore(String status, Instant scheduledAt);
    
    /**
     * Find posts by user and date range
     */
    List<SocialPost> findByUserIdAndScheduledAtBetweenOrderByScheduledAtAsc(
            String userId, Instant startDate, Instant endDate);
    
    /**
     * Find posts by user, status, and date range
     */
    List<SocialPost> findByUserIdAndStatusAndScheduledAtBetweenOrderByScheduledAtAsc(
            String userId, String status, Instant startDate, Instant endDate);
}
