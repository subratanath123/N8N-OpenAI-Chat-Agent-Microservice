package net.ai.chatbot.dao;

import net.ai.chatbot.entity.ChatBotType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatBotTypeDao extends MongoRepository<ChatBotType, String> {
    
    /**
     * Find chatbot type by name
     */
    Optional<ChatBotType> findByName(String name);
    
    /**
     * Find all chatbot types ordered by name
     */
    List<ChatBotType> findAllByOrderByNameAsc();
    
    /**
     * Check if chatbot type exists by name
     */
    boolean existsByName(String name);
}

