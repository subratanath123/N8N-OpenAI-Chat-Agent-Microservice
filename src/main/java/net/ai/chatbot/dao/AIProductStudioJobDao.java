package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AIProductStudioJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIProductStudioJobDao extends MongoRepository<AIProductStudioJob, String> {
    List<AIProductStudioJob> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    Optional<AIProductStudioJob> findByIdAndUserEmail(String id, String userEmail);
    List<AIProductStudioJob> findByUserEmailAndStatusIn(String userEmail, List<String> statuses);
}
