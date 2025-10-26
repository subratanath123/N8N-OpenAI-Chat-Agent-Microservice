package net.ai.chatbot.dao;

import net.ai.chatbot.entity.ChatBot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatBotDao extends MongoRepository<ChatBot, String> {
    
    /**
     * Find chatbots by creator email
     */
    List<ChatBot> findByCreatedBy(String createdBy);
    
    /**
     * Find chatbot by name and creator
     */
    Optional<ChatBot> findByNameAndCreatedBy(String name, String createdBy);
    
    /**
     * Find chatbots by status
     */
    List<ChatBot> findByStatus(String status);
    
    /**
     * Check if chatbot exists by name and creator
     */
    boolean existsByNameAndCreatedBy(String name, String createdBy);
    
    /**
     * Find chatbots created after a specific date
     */
    List<ChatBot> findByCreatedAtAfter(Date date);
}

