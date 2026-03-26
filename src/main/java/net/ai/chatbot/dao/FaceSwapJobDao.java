package net.ai.chatbot.dao;

import net.ai.chatbot.entity.FaceSwapJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaceSwapJobDao extends MongoRepository<FaceSwapJob, String> {
    List<FaceSwapJob> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    Optional<FaceSwapJob> findByIdAndUserEmail(String id, String userEmail);
    List<FaceSwapJob> findByUserEmailAndStatusIn(String userEmail, List<String> statuses);
}
