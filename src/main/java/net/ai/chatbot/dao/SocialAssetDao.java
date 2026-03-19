package net.ai.chatbot.dao;

import net.ai.chatbot.entity.SocialAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for SocialAsset entity.
 * Manages social media assets uploaded and tracked by users.
 */
@Repository
public interface SocialAssetDao extends MongoRepository<SocialAsset, String> {

    /**
     * Find asset by ID and verify user ownership.
     */
    Optional<SocialAsset> findByIdAndUserEmail(String id, String userEmail);

    /**
     * List all social assets for a user.
     */
    Page<SocialAsset> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    /**
     * Find assets by MIME type pattern (e.g., "image/*").
     */
    Page<SocialAsset> findByUserEmailAndMimeTypeRegex(String userEmail, String mimeTypePattern, Pageable pageable);

    /**
     * Search assets by filename.
     */
    Page<SocialAsset> findByUserEmailAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
            String userEmail, String fileName, Pageable pageable);

    /**
     * Find assets created within a date range.
     */
    Page<SocialAsset> findByUserEmailAndCreatedAtBetweenOrderByCreatedAtDesc(
            String userEmail, Instant startTime, Instant endTime, Pageable pageable);

    /**
     * Count all assets for a user.
     */
    long countByUserEmail(String userEmail);
}
