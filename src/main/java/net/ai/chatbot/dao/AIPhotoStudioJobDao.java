package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AIPhotoStudioJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIPhotoStudioJobDao extends MongoRepository<AIPhotoStudioJob, String> {
    List<AIPhotoStudioJob> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    Optional<AIPhotoStudioJob> findByIdAndUserEmail(String id, String userEmail);
    List<AIPhotoStudioJob> findByUserEmailAndStatusIn(String userEmail, List<String> statuses);
}
