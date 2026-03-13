package net.ai.chatbot.dao;

import net.ai.chatbot.entity.social.SocialAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SocialAccountDao extends MongoRepository<SocialAccount, String> {

    List<SocialAccount> findByUserId(String userId);

    List<SocialAccount> findByUserIdAndPlatform(String userId, String platform);

    Optional<SocialAccount> findByIdAndUserId(String id, String userId);

    boolean existsByIdAndUserId(String id, String userId);
}
