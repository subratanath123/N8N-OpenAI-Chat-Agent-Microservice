package net.ai.chatbot.dao;

import net.ai.chatbot.entity.GoogleCalendarToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Google Calendar OAuth tokens
 */
@Repository
public interface GoogleCalendarTokenDao extends MongoRepository<GoogleCalendarToken, String> {
    
    /**
     * Find token by chatbot ID
     */
    Optional<GoogleCalendarToken> findByChatbotId(String chatbotId);
    
    /**
     * Check if token exists for a chatbot
     */
    boolean existsByChatbotId(String chatbotId);
    
    /**
     * Delete token by chatbot ID
     */
    void deleteByChatbotId(String chatbotId);
    
    /**
     * Find all tokens that are expired
     */
    List<GoogleCalendarToken> findByExpiresAtBefore(Date date);
    
    /**
     * Find tokens by creator
     */
    List<GoogleCalendarToken> findByCreatedBy(String createdBy);
}

