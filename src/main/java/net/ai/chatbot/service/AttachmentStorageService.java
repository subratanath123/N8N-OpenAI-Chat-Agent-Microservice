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

import java.io.*;
import java.util.*;

/**
 * Service for storing attachments in MongoDB instead of OpenAI
 * 
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
    
    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    /**
     * Store attachment in MongoDB with binary content
     * 
     * @param attachment the attachment DTO with file data
     * @param chatbotId the chatbot ID
     * @param sessionId the session ID
     * @return AttachmentStorageResult with fileId and download URL
     * @throws IOException if processing fails
     */
    public AttachmentStorageResult storeAttachmentInMongoDB(
            Attachment attachment, 
            String chatbotId, 
            String sessionId) throws IOException {
        
        log.info("Starting attachment storage in MongoDB for: {} (chatbot: {}, session: {})",
                attachment.getName(), chatbotId, sessionId);
        
        try {
            // Generate unique fileId
            String fileId = generateFileId(chatbotId, sessionId, attachment.getName());
            log.debug("Generated fileId: {}", fileId);
            
            // Decode base64 to bytes
            byte[] fileContent = decodeBase64ToBytes(attachment.getFileData());
            log.debug("File decoded. Size: {} bytes", fileContent.length);
            
            // Create document with file content as binary
            long uploadedAt = System.currentTimeMillis();
            Document fileDocument = new Document()
                    // IDs
                    .append("fileId", fileId)
                    .append("chatbotId", chatbotId)
                    .append("sessionId", sessionId)
                    
                    // File information
                    .append("fileName", attachment.getName())
                    .append("mimeType", attachment.getMimeType())
                    .append("fileSize", fileContent.length)
                    
                    // File content as binary (stored in MongoDB)
                    .append("fileContent", new Binary(fileContent))
                    
                    // Metadata
                    .append("uploadedAt", uploadedAt)
                    .append("createdAt", new Date(uploadedAt))
                    .append("status", "stored")
                    .append("source", "mongodb_storage")
                    .append("version", 1);
            
            // Save to MongoDB
            String collectionName = "attachments_" + chatbotId;
            mongoTemplate.getCollection(collectionName).insertOne(fileDocument);
            
            log.info("Attachment stored in MongoDB: collection={}, fileId={}, fileName={}", 
                    collectionName, fileId, attachment.getName());
            
            // Generate download URL
            String downloadUrl = generateDownloadUrl(fileId, chatbotId);
            
            // Return result with download URL
            return AttachmentStorageResult.builder()
                    .fileId(fileId)
                    .fileName(attachment.getName())
                    .mimeType(attachment.getMimeType())
                    .fileSize(fileContent.length)
                    .downloadUrl(downloadUrl)
                    .uploadedAt(uploadedAt)
                    .status("stored")
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to store attachment in MongoDB", e);
            throw new IOException("Failed to store attachment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Retrieve file content from MongoDB by fileId
     * 
     * @param fileId the file ID
     * @param chatbotId the chatbot ID
     * @return file content as bytes
     */
    public byte[] getFileContent(String fileId, String chatbotId) {
        try {
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
            
        } catch (Exception e) {
            log.error("Failed to retrieve file content: fileId={}, chatbotId={}", fileId, chatbotId, e);
            return null;
        }
    }
    
    /**
     * Get file metadata (without content) from MongoDB
     * 
     * @param fileId the file ID
     * @param chatbotId the chatbot ID
     * @return file metadata
     */
    public FileMetadata getFileMetadata(String fileId, String chatbotId) {
        try {
            log.debug("Retrieving file metadata: fileId={}, chatbotId={}", fileId, chatbotId);
            
            String collectionName = "attachments_" + chatbotId;
            Document fileDoc = mongoTemplate.getCollection(collectionName)
                    .find(new Document("fileId", fileId))
                    .first();
            
            if (fileDoc == null) {
                log.warn("File metadata not found: fileId={}", fileId);
                return null;
            }
            
            return FileMetadata.builder()
                    .fileId((String) fileDoc.get("fileId"))
                    .fileName((String) fileDoc.get("fileName"))
                    .mimeType((String) fileDoc.get("mimeType"))
                    .fileSize(((Number) fileDoc.get("fileSize")).longValue())
                    .uploadedAt(((Date) fileDoc.get("uploadedAt")).getTime())
                    .status((String) fileDoc.get("status"))
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to retrieve file metadata: fileId={}", fileId, e);
            return null;
        }
    }
    
    /**
     * Delete attachment from MongoDB
     * 
     * @param fileId the file ID
     * @param chatbotId the chatbot ID
     * @return true if deleted successfully
     */
    public boolean deleteAttachment(String fileId, String chatbotId) {
        try {
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
            
        } catch (Exception e) {
            log.error("Failed to delete attachment: fileId={}", fileId, e);
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
        try {
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
            
        } catch (Exception e) {
            log.error("Failed to list attachments for chatbot: {}", chatbotId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Generate unique fileId
     */
    private String generateFileId(String chatbotId, String sessionId, String fileName) {
        String sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "");
        long timestamp = System.currentTimeMillis();
        return String.format("file_%s_%s_%s_%d", 
                chatbotId, sessionId, sanitizedFileName, timestamp);
    }
    
    /**
     * Generate download URL for the file
     */
    private String generateDownloadUrl(String fileId, String chatbotId) {
        return String.format("%s/api/attachments/download/%s?chatbotId=%s", 
                baseUrl, fileId, chatbotId);
    }
    
    /**
     * Decode base64 string to bytes
     */
    private byte[] decodeBase64ToBytes(String base64Data) throws IOException {
        if (base64Data == null || base64Data.isEmpty()) {
            throw new IOException("File data is empty");
        }
        
        try {
            return java.util.Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid base64 data: " + e.getMessage());
        }
    }
}

