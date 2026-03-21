package net.ai.chatbot.dao;

import net.ai.chatbot.entity.WorkflowConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowConfigDao extends MongoRepository<WorkflowConfig, String> {

    Optional<WorkflowConfig> findByChatbotId(String chatbotId);

    boolean existsByChatbotId(String chatbotId);
}

