package net.ai.chatbot.dao;

import net.ai.chatbot.entity.MediaAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for MediaAsset entity.
 */
@Repository
public interface MediaAssetDao extends MongoRepository<MediaAsset, String> {

    /**
     * Find asset by ID and user (for ownership verification)
     */
    Optional<MediaAsset> findByIdAndUserEmail(String id, String userEmail);

    /**
     * Find all assets for a user with pagination
     */
    Page<MediaAsset> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    /**
     * Find assets by user and MIME type prefix with pagination
     */
    @Query("{ 'userEmail': ?0, 'mimeType': { $regex: ?1, $options: 'i' } }")
    Page<MediaAsset> findByUserEmailAndMimeTypeRegex(String userEmail, String mimeTypePattern, Pageable pageable);

    /**
     * Find assets by user and filename search with pagination
     */
    Page<MediaAsset> findByUserEmailAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
            String userEmail, String search, Pageable pageable);

    /**
     * Count assets by user
     */
    long countByUserEmail(String userEmail);

    /**
     * Delete by ID and user (for secure deletion)
     */
    void deleteByIdAndUserEmail(String id, String userEmail);

    /**
     * Find all assets by user and folder path starting with (for folder listing)
     */
    @Query("{ 'userEmail': ?0, 'folderPath': { $regex: ?1 } }")
    java.util.List<MediaAsset> findByUserEmailAndFolderPathStartingWith(String userEmail, String folderPathPrefix);
}
