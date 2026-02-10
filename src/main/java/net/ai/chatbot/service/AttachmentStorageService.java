package net.ai.chatbot.service;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.AttachmentStorageResult;
import net.ai.chatbot.dto.FileMetadata;
import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Service for storing attachments in MongoDB instead of OpenAI
 * <p>
 * Benefits:
 * - Full control over file storage
 * - Can download files directly via REST API using fileId
 * - Easy integration with N8N for file analysis
 * - No OpenAI file upload overhead
 * - File content stored as binary in MongoDB
 */
@Service
@Slf4j
public class AttachmentStorageService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Store attachment in MongoDB with binary content
     *
     * @param attachment the attachment DTO with file data
     * @param chatbotId  the chatbot ID
     * @return AttachmentStorageResult with fileId and download URL
     * @throws IOException if processing fails
     */
    public AttachmentStorageResult storeAttachmentInMongoDB(
            Attachment attachment,
            String chatbotId) throws IOException {

        log.info("Starting attachment storage in MongoDB for: {} (chatbot: {}, session: {})",
                attachment.getName(), chatbotId);

        byte[] fileContent = attachment.getData();
        log.debug("File decoded. Size: {} bytes", fileContent.length);

        // Save to MongoDB
        String collectionName = "attachments_" + chatbotId;
        Attachment saved = mongoTemplate.save(attachment);

        log.info("Attachment stored in MongoDB: collection={}, fileId={}, fileName={}",
                collectionName, saved.getId(), attachment.getName());

        return AttachmentStorageResult.builder()
                .fileId(saved.getId())
                .fileName(attachment.getName())
                .mimeType(attachment.getMimeType())
                .fileSize(attachment.getLength())
                .uploadedAt(attachment.getUploadedAt().getTime())
                .status("stored")
                .build();
    }

    /**
     * Retrieve file content from MongoDB by fileId
     *
     * @param fileId    the file ID
     * @param chatbotId the chatbot ID
     * @return file content as bytes
     */
    public byte[] getFileContent(String fileId, String chatbotId) {
        log.debug("Retrieving file content: fileId={}, chatbotId={}", fileId, chatbotId);

        String collectionName = "attachments_" + chatbotId;
        Document fileDoc = mongoTemplate.getCollection(collectionName)
                .find(new Document("fileId", fileId))
                .first();

        if (fileDoc == null) {
            log.warn("File not found: fileId={}, chatbotId={}", fileId, chatbotId);
            return null;
        }

        Binary binaryContent = fileDoc.get("fileContent", Binary.class);
        if (binaryContent == null) {
            log.warn("File content not found for fileId: {}", fileId);
            return null;
        }

        byte[] content = binaryContent.getData();
        log.info("File retrieved successfully: fileId={}, size={} bytes", fileId, content.length);

        return content;
    }

    /**
     * Get file metadata (without content) from MongoDB
     *
     * @param fileId    the file ID
     * @param chatbotId the chatbot ID
     * @return file metadata
     */
    public FileMetadata getFileMetadata(String fileId, String chatbotId) {
        log.debug("Retrieving file metadata: fileId={}, chatbotId={}", fileId, chatbotId);

        Document fileDoc = mongoTemplate.getCollection("attachment")
                .find(new Document("fileId", fileId))
                .first();

        if (fileDoc == null) {
            log.warn("File metadata not found: fileId={}", fileId);
            return null;
        }

        return FileMetadata.builder()
                .fileId((String) fileDoc.get("id"))
                .fileName((String) fileDoc.get("name"))
                .mimeType((String) fileDoc.get("type"))
                .fileSize(((Number) fileDoc.get("size")).longValue())
                .uploadedAt(((Date) fileDoc.get("uploadedAt")).getTime())
                .status("stored")
                .build();
    }

    /**
     * Delete attachment from MongoDB
     *
     * @param fileId    the file ID
     * @param chatbotId the chatbot ID
     * @return true if deleted successfully
     */
    public boolean deleteAttachment(String fileId, String chatbotId) {
        log.info("Deleting attachment: fileId={}, chatbotId={}", fileId, chatbotId);

        String collectionName = "attachments_" + chatbotId;
        long deletedCount = mongoTemplate.getCollection(collectionName)
                .deleteOne(new Document("fileId", fileId))
                .getDeletedCount();

        if (deletedCount > 0) {
            log.info("Attachment deleted successfully: fileId={}", fileId);
            return true;
        } else {
            log.warn("Attachment not found for deletion: fileId={}", fileId);
            return false;
        }
    }

    /**
     * List all attachments for a chatbot
     *
     * @param chatbotId the chatbot ID
     * @return list of file metadata (without binary content)
     */
    public List<FileMetadata> listAttachments(String chatbotId) {
        log.debug("Listing attachments for chatbot: {}", chatbotId);

        String collectionName = "attachments_" + chatbotId;
        List<FileMetadata> files = new ArrayList<>();

        mongoTemplate.getCollection(collectionName)
                .find()
                .forEach(doc -> {
                    files.add(FileMetadata.builder()
                            .fileId((String) doc.get("fileId"))
                            .fileName((String) doc.get("fileName"))
                            .mimeType((String) doc.get("mimeType"))
                            .fileSize(((Number) doc.get("fileSize")).longValue())
                            .uploadedAt(((Date) doc.get("uploadedAt")).getTime())
                            .status((String) doc.get("status"))
                            .build());
                });

        log.info("Found {} attachments for chatbot: {}", files.size(), chatbotId);
        return files;
    }

}

