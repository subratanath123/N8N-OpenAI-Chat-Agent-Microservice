package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AIImageJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIImageJobDao extends MongoRepository<AIImageJob, String> {
    List<AIImageJob> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    Optional<AIImageJob> findByIdAndUserEmail(String id, String userEmail);
    List<AIImageJob> findByStatusIn(List<String> statuses);
}
