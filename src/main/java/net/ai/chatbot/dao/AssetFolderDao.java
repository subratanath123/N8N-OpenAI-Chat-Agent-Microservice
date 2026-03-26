package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AssetFolder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AssetFolder entity.
 */
@Repository
public interface AssetFolderDao extends MongoRepository<AssetFolder, String> {

    /**
     * Find folder by user email and folder path
     */
    Optional<AssetFolder> findByUserEmailAndFolderPath(String userEmail, String folderPath);

    /**
     * Find all folders for a user with path starting with prefix
     */
    @Query("{ 'userEmail': ?0, 'folderPath': { $regex: ?1 } }")
    List<AssetFolder> findByUserEmailAndFolderPathStartingWith(String userEmail, String folderPathPrefix);

    /**
     * Delete folder by user email and folder path
     */
    void deleteByUserEmailAndFolderPath(String userEmail, String folderPath);

    /**
     * Check if folder exists
     */
    boolean existsByUserEmailAndFolderPath(String userEmail, String folderPath);
}
