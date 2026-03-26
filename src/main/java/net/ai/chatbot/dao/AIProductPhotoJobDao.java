package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AIProductPhotoJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIProductPhotoJobDao extends MongoRepository<AIProductPhotoJob, String> {
    List<AIProductPhotoJob> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    Optional<AIProductPhotoJob> findByIdAndUserEmail(String id, String userEmail);
    List<AIProductPhotoJob> findByUserEmailAndStatusIn(String userEmail, List<String> statuses);
}
