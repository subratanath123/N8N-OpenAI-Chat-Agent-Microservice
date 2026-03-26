package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AIVideoJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIVideoJobDao extends MongoRepository<AIVideoJob, String> {
    List<AIVideoJob> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    Optional<AIVideoJob> findByIdAndUserEmail(String id, String userEmail);
    List<AIVideoJob> findByStatusIn(List<String> statuses);
}
