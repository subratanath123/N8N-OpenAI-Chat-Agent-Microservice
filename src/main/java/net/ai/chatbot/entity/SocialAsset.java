package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Media asset specifically for social media posts.
 * Stored in MongoDB with metadata, file content in Attachment collection.
 * Different from MediaAsset (personal/user media).
 */
@Document(collection = "social_assets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialAsset {

    @Id
    private String id;

    /**
     * User email from Clerk JWT (indexed for fast queries)
     */
    @Indexed
    private String userEmail;

    /**
     * Reference to the Attachment ID where file is stored
     * (AttachmentStorageService stores it in Attachment collection)
     */
    @Indexed
    private String attachmentId;

    /**
     * Original filename for display
     */
    private String fileName;

    /**
     * MIME type (e.g., image/jpeg, video/mp4)
     */
    private String mimeType;

    /**
     * File size in bytes
     */
    private Long sizeBytes;

    /**
     * Download URL generated from attachmentId
     * Format: {baseUrl}/v1/api/user/attachments/download/{attachmentId}
     */
    private String downloadUrl;

    /**
     * Image width (if applicable)
     */
    private Integer width;

    /**
     * Image height (if applicable)
     */
    private Integer height;

    /**
     * Video duration in milliseconds (if applicable)
     */
    private Long durationMs;

    /**
     * Upload timestamp
     */
    private Instant createdAt;
}
