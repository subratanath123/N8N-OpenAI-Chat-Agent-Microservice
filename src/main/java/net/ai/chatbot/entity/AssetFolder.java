package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Represents a folder in the Assets hierarchy.
 * Tracks folders even when they don't contain assets yet.
 */
@Document(collection = "asset_folders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "userEmail_folderPath", def = "{'userEmail': 1, 'folderPath': 1}", unique = true)
})
public class AssetFolder {

    @Id
    private String id;

    /**
     * User email from Clerk JWT (indexed for fast queries)
     */
    @Indexed
    private String userEmail;

    /**
     * Full folder path (e.g., "Photos/Vacation")
     */
    private String folderPath;

    /**
     * Folder creation timestamp
     */
    private Instant createdAt;

    /**
     * Optional folder description
     */
    private String description;
}
