package net.ai.chatbot.service.n8n;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Attachment;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for saving attachments to OpenAI Vector Store
 * 
 * Follows OpenAI's official Files API documentation:
 * https://platform.openai.com/docs/guides/tools/file-search
 * 
 * Workflow:
 * 1. Upload file to OpenAI Files API → get file_id
 * 2. Add file_id to OpenAI Vector Store → get vector_store_file_id
 * 3. Store metadata in MongoDB for reference
 * 4. Return vector_store_file_id for N8N use
 */
@Service
@Slf4j
public class AttachmentSaveService {
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.api.base.url:https://api.openai.com/v1}")
    private String openaiBaseUrl;
    
    @Value("${file.upload.path:uploads}")
    private String uploadPath;
    
    private final RestTemplate restTemplate;
    private final MongoTemplate mongoTemplate;
    
    // Cache for vector store IDs (chatbotId → vectorStoreId)
    private final Map<String, String> vectorStoreCache = new ConcurrentHashMap<>();
    
    @Autowired
    public AttachmentSaveService(RestTemplate restTemplate, MongoTemplate mongoTemplate) {
        this.restTemplate = restTemplate;
        this.mongoTemplate = mongoTemplate;
    }
    
    /**
     * Save attachment from MultipartFile (direct upload without base64)
     * This is the preferred method for file uploads - no base64 encoding overhead
     * 
     * @param multipartFile the multipart file from HTTP request
     * @param chatbotId the chatbot ID
     * @param sessionId the session ID
     * @return AttachmentSaveResult containing vectorStoreId and vectorStoreFileId
     * @throws IOException if file operations fail
     */
    public AttachmentSaveResult saveAttachmentFromMultipart(MultipartFile multipartFile, String chatbotId, String sessionId)
            throws IOException {
        
        log.info("Starting attachment save workflow from Multipart for: {} (chatbot: {}, session: {})",
                multipartFile.getOriginalFilename(), chatbotId, sessionId);
        
        // ============================================================
        // STEP 1: VALIDATE MULTIPART FILE
        // ============================================================
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("File is required and cannot be empty");
        }
        
        validateMultipartFile(multipartFile);
        log.debug("Multipart file validation passed: {}", multipartFile.getOriginalFilename());
        
        // ============================================================
        // STEP 2: SAVE FILE TO DISK TEMPORARILY
        // ============================================================
        String tempFilePath = saveMultipartFileToDisk(multipartFile, chatbotId, sessionId);
        log.debug("Multipart file saved temporarily to disk: {}", tempFilePath);
        
        String fileId = null;
        String vectorStoreId = null;
        String vectorStoreFileId = null;
        
        try {
            // ============================================================
            // STEP 3: UPLOAD TO OPENAI FILES API
            // ============================================================
            fileId = uploadToOpenAIFilesAPI(tempFilePath, multipartFile.getOriginalFilename(), 
                    multipartFile.getContentType());
            log.info("File uploaded to OpenAI Files API with file_id: {}", fileId);
            
            // ============================================================
            // STEP 4: ADD FILE TO OPENAI VECTOR STORE
            // ============================================================
            AttachmentSaveResult result = addToVectorStoreAndGetIds(fileId, chatbotId, sessionId);
            vectorStoreId = result.getVectorStoreId();
            vectorStoreFileId = result.getVectorStoreFileId();
            
            log.info("File added to OpenAI Vector Store: vectorStoreId={}, vector_store_file_id={}", 
                    vectorStoreId, vectorStoreFileId);
            
            // ============================================================
            // STEP 5: STORE METADATA IN MONGODB
            // ============================================================
            saveAttachmentMetadataFromMultipart(multipartFile, fileId, vectorStoreFileId, 
                    chatbotId, sessionId, vectorStoreId);
            log.info("Attachment metadata saved to MongoDB");
            
            // ============================================================
            // STEP 6: DELETE TEMPORARY FILE
            // ============================================================
            deleteTemporaryFile(tempFilePath);
            log.debug("Temporary file deleted: {}", tempFilePath);
            
        } catch (Exception e) {
            log.error("Error in attachment save workflow from Multipart", e);
            
            // Cleanup: Delete temporary file on error
            try {
                deleteTemporaryFile(tempFilePath);
            } catch (Exception cleanupError) {
                log.warn("Failed to cleanup temporary file: {}", tempFilePath, cleanupError);
            }
            
            throw new RuntimeException("Failed to save attachment: " + e.getMessage(), e);
        }
        
        log.info("Attachment successfully saved to OpenAI Vector Store: vectorStoreId={}, vectorStoreFileId={}", 
                vectorStoreId, vectorStoreFileId);
        
        return AttachmentSaveResult.builder()
                .vectorStoreId(vectorStoreId)
                .vectorStoreFileId(vectorStoreFileId)
                .build();
    }
    
    /**
     * LEGACY: Save attachment from base64-encoded data (deprecated - use saveAttachmentFromMultipart instead)
     * Kept for backward compatibility
     * 
     * @param attachment the attachment DTO with base64 data
     * @param chatbotId the chatbot ID
     * @param sessionId the session ID
     * @return AttachmentSaveResult
     * @throws IOException if file operations fail
     * @deprecated Use {@link #saveAttachmentFromMultipart(MultipartFile, String, String)} instead
     */
    @Deprecated
    public AttachmentSaveResult saveAttachment(Attachment attachment, String chatbotId, String sessionId) 
            throws IOException {
        
        log.warn("DEPRECATED: Using base64-encoded attachments. Consider using MultipartFile instead.");
        log.info("Starting attachment save workflow for: {} (chatbot: {}, session: {})",
                attachment != null ? attachment.getName() : "null", chatbotId, sessionId);
        
        // ============================================================
        // STEP 1: VALIDATE ATTACHMENT
        // ============================================================
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment cannot be null");
        }
        
        validateAttachment(attachment);
        log.debug("Attachment validation passed: {}", attachment.getName());
        
        // ============================================================
        // STEP 2: SAVE FILE TO DISK TEMPORARILY
        // ============================================================
        String tempFilePath = saveAttachmentToDisk(attachment, chatbotId, sessionId);
        log.debug("Attachment saved temporarily to disk: {}", tempFilePath);
        
        String fileId = null;
        String vectorStoreId = null;
        String vectorStoreFileId = null;
        
        try {
            // ============================================================
            // STEP 3: UPLOAD TO OPENAI FILES API
            // ============================================================
            fileId = uploadToOpenAIFilesAPI(tempFilePath, attachment);
            log.info("File uploaded to OpenAI Files API with file_id: {}", fileId);
            
            // ============================================================
            // STEP 4: ADD FILE TO OPENAI VECTOR STORE (also gets vectorStoreId)
            // ============================================================
            AttachmentSaveResult result = addToVectorStoreAndGetIds(fileId, chatbotId, sessionId);
            vectorStoreId = result.getVectorStoreId();
            vectorStoreFileId = result.getVectorStoreFileId();

            log.info("File added to OpenAI Vector Store: vectorStoreId={}, vector_store_file_id={}", 
                    vectorStoreId, vectorStoreFileId);
            
            // ============================================================
            // STEP 5: STORE METADATA IN MONGODB
            // ============================================================
            saveAttachmentMetadata(attachment, fileId, vectorStoreFileId, chatbotId, sessionId, vectorStoreId);
            log.info("Attachment metadata saved to MongoDB");
            
            // ============================================================
            // STEP 6: DELETE TEMPORARY FILE
            // ============================================================
            deleteTemporaryFile(tempFilePath);
            log.debug("Temporary file deleted: {}", tempFilePath);
            
        } catch (Exception e) {
            log.error("Error in attachment save workflow", e);
            
            // Cleanup: Delete temporary file on error
            try {
                deleteTemporaryFile(tempFilePath);
            } catch (Exception cleanupError) {
                log.warn("Failed to cleanup temporary file: {}", tempFilePath, cleanupError);
            }
            
            throw new RuntimeException("Failed to save attachment: " + e.getMessage(), e);
        }
        
        log.info("Attachment successfully saved to OpenAI Vector Store: vectorStoreId={}, vectorStoreFileId={}", 
                vectorStoreId, vectorStoreFileId);
        
        return AttachmentSaveResult.builder()
                .vectorStoreId(vectorStoreId)
                .vectorStoreFileId(vectorStoreFileId)
                .build();
    }
    
    /**
     * Validate multipart file
     */
    private void validateMultipartFile(MultipartFile file) throws IOException {
        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            throw new IllegalArgumentException("File name is required");
        }
        
        if (file.getSize() <= 0) {
            throw new IllegalArgumentException("File is empty");
        }
        
        // 100MB limit
        if (file.getSize() > 104857600) {
            throw new IllegalArgumentException("File size exceeds 100MB limit. Size: " + file.getSize() + " bytes");
        }
        
        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isEmpty()) {
            throw new IllegalArgumentException("MIME type is required");
        }
        
        // Validate MIME type (whitelist)
        if (!isAllowedMimeType(mimeType)) {
            throw new IllegalArgumentException("MIME type '" + mimeType + "' is not allowed");
        }
    }
    
    /**
     * Validate attachment (MIME type, size, format)
     */
    private void validateAttachment(Attachment attachment) {
        if (attachment.getName() == null || attachment.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Attachment name is required");
        }

        if (attachment.getSize() <= 0) {
            throw new IllegalArgumentException("Invalid file size");
        }
        
        // 100MB limit
        if (attachment.getSize() > 104857600) {
            throw new IllegalArgumentException("File size exceeds 100MB limit");
        }
        
        if (attachment.getMimeType() == null || attachment.getMimeType().isEmpty()) {
            throw new IllegalArgumentException("MIME type is required");
        }
        
        // Validate MIME type (whitelist)
        if (!isAllowedMimeType(attachment.getMimeType())) {
            throw new IllegalArgumentException("MIME type '" + attachment.getMimeType() + "' is not allowed");
        }
    }
    
    /**
     * Save multipart file to disk temporarily for OpenAI upload
     * 
     * @return file path on disk
     */
    private String saveMultipartFileToDisk(MultipartFile multipartFile, String chatbotId, String sessionId) 
            throws IOException {
        
        // Create directory: uploads/{chatbotId}/{sessionId}/
        Path dirPath = Paths.get(uploadPath, chatbotId, sessionId);
        Files.createDirectories(dirPath);
        
        // Sanitize filename
        String sanitizedName = sanitizeFilename(multipartFile.getOriginalFilename());
        Path filePath = dirPath.resolve(sanitizedName);
        
        // Write multipart file bytes directly (no base64 decoding needed)
        Files.write(filePath, multipartFile.getBytes());
        
        log.debug("Multipart file saved to disk: {}", filePath);
        return filePath.toString();
    }
    
    /**
     * Save attachment to disk temporarily for OpenAI upload
     * 
     * @return file path on disk
     */
    private String saveAttachmentToDisk(Attachment attachment, String chatbotId, String sessionId) 
            throws IOException {
        
        // Create directory: uploads/{chatbotId}/{sessionId}/
        Path dirPath = Paths.get(uploadPath, chatbotId, sessionId);
        Files.createDirectories(dirPath);
        
        // Sanitize filename
        String sanitizedName = sanitizeFilename(attachment.getName());
        Path filePath = dirPath.resolve(sanitizedName);
        
        // Decode Base64 and write to file
        byte[] fileData = Base64.getDecoder().decode(attachment.getData());
        Files.write(filePath, fileData);
        
        log.debug("File saved to disk: {}", filePath);
        return filePath.toString();
    }
    
    /**
     * Upload file to OpenAI Files API
     * 
     * Endpoint: POST https://api.openai.com/v1/files
     * 
     * @param filePath path to file on disk
     * @param filename filename for upload
     * @param mimeType MIME type of file
     * @return file_id from OpenAI
     */
    private String uploadToOpenAIFilesAPI(String filePath, String filename, String mimeType) throws IOException {
        log.debug("Uploading file to OpenAI Files API: {}", filename);
        
        try {
            File file = new File(filePath);
            
            // Build multipart request with proper boundary
            String boundary = "----FormBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            // Write file part
            outputStream.write(("--" + boundary + "\r\n").getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" 
                    + filename + "\"\r\n").getBytes());
            outputStream.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes());
            outputStream.write(Files.readAllBytes(file.toPath()));
            outputStream.write("\r\n".getBytes());
            
            // Write purpose part
            outputStream.write(("--" + boundary + "\r\n").getBytes());
            outputStream.write("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n".getBytes());
            outputStream.write("assistants".getBytes());
            outputStream.write("\r\n".getBytes());
            outputStream.write(("--" + boundary + "--\r\n").getBytes());
            
            // Create request headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            // Make request
            HttpEntity<byte[]> entity = new HttpEntity<>(outputStream.toByteArray(), headers);
            
            String url = openaiBaseUrl + "/files";
            log.debug("Uploading to OpenAI endpoint: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            if (response == null || !response.containsKey("id")) {
                throw new RuntimeException("Invalid response from OpenAI Files API");
            }
            
            String fileId = (String) response.get("id");
            log.info("File uploaded successfully with file_id: {}", fileId);
            
            return fileId;
            
        } catch (Exception e) {
            log.error("Failed to upload file to OpenAI Files API", e);
            throw new IOException("Failed to upload to OpenAI: " + e.getMessage(), e);
        }
    }
    
    /**
     * Upload file to OpenAI Files API (overload for Attachment DTO)
     * 
     * Endpoint: POST https://api.openai.com/v1/files
     * 
     * @param filePath path to file on disk
     * @param attachment attachment metadata
     * @return file_id from OpenAI
     */
    private String uploadToOpenAIFilesAPI(String filePath, Attachment attachment) throws IOException {
        return uploadToOpenAIFilesAPI(filePath, attachment.getName(), attachment.getMimeType());
    }
    
    
    /**
     * Add file to OpenAI Vector Store
     * 
     * Endpoint: POST https://api.openai.com/v1/vector_stores/{vector_store_id}/files
     * 
     * @param fileId file ID from OpenAI Files API
     * @param chatbotId chatbot ID for context (not sent to OpenAI)
     * @param sessionId session ID for context (not sent to OpenAI)
     * @return vector_store_file_id for N8N reference
     */
    /**
     * Add file to OpenAI Vector Store and return both vectorStoreId and vectorStoreFileId
     * 
     * ✨ NOW WITH METADATA ATTRIBUTES SUPPORT ✨
     * Stores fileId and other metadata directly in OpenAI Vector Store (up to 16 key-value pairs)
     * 
     * Endpoint: POST https://api.openai.com/v1/vector_stores/{vector_store_id}/files
     * 
     * Metadata stored in OpenAI:
     * - fileId: The OpenAI file ID
     * - chatbotId: The chatbot ID
     * - sessionId: The session ID
     * - uploadedAt: Timestamp of upload
     * 
     * @param fileId file ID from OpenAI Files API
     * @param chatbotId chatbot ID for context
     * @param sessionId session ID for context
     * @return AttachmentSaveResult containing both vectorStoreId and vectorStoreFileId
     */
    private AttachmentSaveResult addToVectorStoreAndGetIds(String fileId, String chatbotId, String sessionId) {
        return addToVectorStoreAndGetIds(fileId, chatbotId, sessionId, null);
    }
    
    /**
     * Add file to OpenAI Vector Store with custom metadata attributes
     * 
     * ✨ ADVANCED: Allows adding custom metadata attributes (up to 16 key-value pairs)
     * 
     * @param fileId file ID from OpenAI Files API
     * @param chatbotId chatbot ID for context
     * @param sessionId session ID for context
     * @param customAttributes optional custom metadata attributes (will be merged with default attributes)
     * @return AttachmentSaveResult containing both vectorStoreId and vectorStoreFileId
     */
    public AttachmentSaveResult addToVectorStoreWithMetadata(String fileId, String chatbotId, String sessionId, Map<String, Object> customAttributes) {
        return addToVectorStoreAndGetIds(fileId, chatbotId, sessionId, customAttributes);
    }
    
    /**
     * Internal method: Add file to OpenAI Vector Store with optional custom metadata
     */
    private AttachmentSaveResult addToVectorStoreAndGetIds(String fileId, String chatbotId, String sessionId, Map<String, Object> customAttributes) {
        log.debug("Adding file to vector store: fileId={}, chatbotId={}", fileId, chatbotId);
        
        try {
            // Get or create vector store for this chatbot
            String vectorStoreId = getOrCreateVectorStore(chatbotId);
            log.debug("Using vector store: {} for chatbot: {}", vectorStoreId, chatbotId);
            
            // Create request body with metadata attributes
            // ✨ METADATA SUPPORT: OpenAI Vector Store Files API now supports attributes parameter
            // You can store up to 16 key-value pairs (strings, booleans, or numbers)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("file_id", fileId);
            requestBody.put("chunking_strategy", new HashMap<String, String>() {{
                put("type", "auto");
            }});
            
            // ✨ ADD METADATA ATTRIBUTES - Store metadata directly in OpenAI Vector Store
            Map<String, Object> attributes = new HashMap<>();
            
            // Default attributes (always included)
            attributes.put("fileId", fileId);  // Store fileId as metadata
            attributes.put("chatbotId", chatbotId);
            attributes.put("sessionId", sessionId);
            attributes.put("uploadedAt", String.valueOf(System.currentTimeMillis()));
            
            // Merge custom attributes if provided
            if (customAttributes != null && !customAttributes.isEmpty()) {
                // Only add custom attributes that don't exceed 16 total attributes
                int attributeCount = attributes.size();
                for (Map.Entry<String, Object> entry : customAttributes.entrySet()) {
                    if (attributeCount >= 16) {
                        log.warn("Cannot add more attributes. Maximum 16 attributes allowed. Skipping: {}", entry.getKey());
                        break;
                    }
                    if (!attributes.containsKey(entry.getKey())) {
                        attributes.put(entry.getKey(), entry.getValue());
                        attributeCount++;
                    }
                }
            }
            
            requestBody.put("attributes", attributes);
            
            log.debug("Adding {} metadata attributes to vector store file", attributes.size());
            log.debug("Metadata attributes: {}", attributes);
            
            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("OpenAI-Beta", "assistants=v2");
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Make request
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            String url = String.format("%s/vector_stores/%s/files", openaiBaseUrl, vectorStoreId);
            log.debug("Adding to vector store endpoint: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            log.debug("OpenAI Vector Store Response: {}", response);
            
            if (response == null) {
                throw new RuntimeException("Null response from OpenAI Vector Store API");
            }
            
            // The response contains the vector store file ID
            // Response structure: { "id": "vs_...", "object": "vector_store.file", "status": "...", ... }
            String vectorStoreFileId = (String) response.get("id");
            
            if (vectorStoreFileId == null) {
                log.error("No 'id' field in response. Available keys: {}", response.keySet());
                throw new RuntimeException("No 'id' field in OpenAI Vector Store API response");
            }
            
            String status = (String) response.get("status");
            
            log.info("File added to vector store: vectorStoreId={}, vectorStoreFileId={}, status={}, chatbot={}, session={}", 
                    vectorStoreId, vectorStoreFileId, status, chatbotId, sessionId);
            
            // Return both IDs
            return AttachmentSaveResult.builder()
                    .vectorStoreId(vectorStoreId)
                    .vectorStoreFileId(vectorStoreFileId)
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to add file to vector store for chatbotId: {}", chatbotId, e);
            throw new RuntimeException("Failed to add to vector store: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get existing vector store for chatbot or create new one if doesn't exist
     * 
     * @param chatbotId the chatbot ID
     * @return vector_store_id
     */
    private String getOrCreateVectorStore(String chatbotId) {
        log.debug("Getting or creating vector store for chatbot: {}", chatbotId);
        
        // Check cache first
        if (vectorStoreCache.containsKey(chatbotId)) {
            String cachedId = vectorStoreCache.get(chatbotId);
            log.debug("Using cached vector store for chatbot {}: {}", chatbotId, cachedId);
            return cachedId;
        }
        
        try {
            // Check MongoDB for stored vector store ID
            String vectorStoreId = getVectorStoreIdFromMongoDB(chatbotId);
            if (vectorStoreId != null) {
                vectorStoreCache.put(chatbotId, vectorStoreId);
                log.info("Found vector store in MongoDB: {} for chatbot {}", vectorStoreId, chatbotId);
                return vectorStoreId;
            }
            
            // Create new vector store
            vectorStoreId = createNewVectorStore(chatbotId);
            
            // Cache it
            vectorStoreCache.put(chatbotId, vectorStoreId);
            
            // Store in MongoDB
            saveVectorStoreIdToMongoDB(chatbotId, vectorStoreId);
            
            log.info("Created new vector store: {} for chatbot: {}", vectorStoreId, chatbotId);
            return vectorStoreId;
            
        } catch (Exception e) {
            log.error("Failed to get or create vector store for chatbot: {}", chatbotId, e);
            throw new RuntimeException("Failed to get or create vector store: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create a new vector store in OpenAI
     * 
     * Endpoint: POST https://api.openai.com/v1/vector_stores
     * 
     * @param chatbotId the chatbot ID
     * @return vector_store_id
     */
    private String createNewVectorStore(String chatbotId) {
        log.debug("Creating new vector store for chatbot: {}", chatbotId);
        
        try {
            // Create request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", "vector_store_" + chatbotId);
            
            // Optional: Add metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("chatbotId", chatbotId);
            metadata.put("createdAt", String.valueOf(System.currentTimeMillis()));
            requestBody.put("metadata", metadata);
            
            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("OpenAI-Beta", "assistants=v2");
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Make request
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            String url = openaiBaseUrl + "/vector_stores";
            log.debug("Creating vector store endpoint: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            if (response == null || !response.containsKey("id")) {
                throw new RuntimeException("Invalid response from OpenAI Vector Store creation API");
            }
            
            String vectorStoreId = (String) response.get("id");
            log.info("Successfully created vector store: {} for chatbot: {}", vectorStoreId, chatbotId);
            
            return vectorStoreId;
            
        } catch (Exception e) {
            log.error("Failed to create vector store for chatbot: {}", chatbotId, e);
            throw new RuntimeException("Failed to create vector store: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get vector store ID from MongoDB for a chatbot
     * 
     * @param chatbotId the chatbot ID
     * @return vector_store_id or null if not found
     */
    private String getVectorStoreIdFromMongoDB(String chatbotId) {
        try {
            String collectionName = "chatbot_vector_stores";
            Document doc = mongoTemplate.getCollection(collectionName).find(
                    new Document("chatbotId", chatbotId)
            ).first();
            
            if (doc != null) {
                String vectorStoreId = doc.getString("vectorStoreId");
                log.debug("Found vector store in MongoDB: {} for chatbot: {}", vectorStoreId, chatbotId);
                return vectorStoreId;
            }
        } catch (Exception e) {
            log.warn("Failed to get vector store ID from MongoDB: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Save vector store ID to MongoDB for future reference
     * 
     * @param chatbotId the chatbot ID
     * @param vectorStoreId the vector store ID
     */
    private void saveVectorStoreIdToMongoDB(String chatbotId, String vectorStoreId) {
        try {
            String collectionName = "chatbot_vector_stores";
            
            Document doc = new Document()
                    .append("chatbotId", chatbotId)
                    .append("vectorStoreId", vectorStoreId)
                    .append("createdAt", new Date())
                    .append("status", "active");
            
            // Check if already exists
            Document existing = mongoTemplate.getCollection(collectionName).find(
                    new Document("chatbotId", chatbotId)
            ).first();
            
            if (existing != null) {
                // Update existing
                mongoTemplate.getCollection(collectionName).updateOne(
                        new Document("chatbotId", chatbotId),
                        new Document("$set", doc)
                );
                log.debug("Updated vector store mapping in MongoDB for chatbot: {}", chatbotId);
            } else {
                // Insert new
                mongoTemplate.getCollection(collectionName).insertOne(doc);
                log.debug("Saved vector store mapping to MongoDB for chatbot: {}", chatbotId);
            }
        } catch (Exception e) {
            log.warn("Failed to save vector store ID to MongoDB: {}", e.getMessage());
            // Non-critical - don't fail the workflow
        }
    }
    
    /**
     * Store multipart attachment metadata in MongoDB for reference
     * MongoDB only stores metadata, not the actual file content
     * 
     * @param multipartFile multipart file from HTTP request
     * @param fileId OpenAI file ID
     * @param vectorStoreFileId OpenAI vector store file ID
     * @param chatbotId chatbot ID
     * @param sessionId session ID
     * @param vectorStoreId OpenAI vector store ID
     */
    private void saveAttachmentMetadataFromMultipart(MultipartFile multipartFile, String fileId,
                                                     String vectorStoreFileId, String chatbotId, String sessionId,
                                                     String vectorStoreId) {
        
        long uploadedAt = System.currentTimeMillis();
        
        Document metadata = new Document()
                // OpenAI identifiers (for reference)
                .append("fileId", fileId)
                .append("vectorStoreId", vectorStoreId)
                .append("vectorStoreFileId", vectorStoreFileId)
                
                // Context information
                .append("chatbotId", chatbotId)
                .append("sessionId", sessionId)
                
                // Original attachment information
                .append("originalName", multipartFile.getOriginalFilename())
                .append("mimeType", multipartFile.getContentType())
                .append("fileSize", multipartFile.getSize())
                
                // Timestamps
                .append("uploadedAt", uploadedAt)
                .append("createdAt", new Date(uploadedAt))
                
                // Status and version
                .append("status", "stored")
                .append("source", "openai_vector_store")
                .append("uploadMethod", "multipart")
                .append("version", 1);
        
        try {
            // Save to MongoDB collection
            String collectionName = "attachments_" + chatbotId;
            mongoTemplate.getCollection(collectionName).insertOne(metadata);
            
            log.info("Attachment metadata saved to MongoDB: collection={}, fileId={}, uploadMethod=multipart", 
                    collectionName, fileId);
            
        } catch (Exception e) {
            log.warn("Failed to save metadata to MongoDB (non-critical): {}", e.getMessage());
            // Non-critical: logging only, don't fail the workflow
        }
    }
    
    /**
     * Store attachment metadata in MongoDB for reference
     * MongoDB only stores metadata, not the actual file content
     * 
     * This serves as a metadata bridge since OpenAI Vector Store Files don't support metadata.
     * All custom metadata is stored here and linked via fileId and vectorStoreFileId.
     * 
     * @param attachment original attachment
     * @param fileId OpenAI file ID
     * @param vectorStoreFileId OpenAI vector store file ID
     * @param chatbotId chatbot ID
     * @param sessionId session ID
     */
    private void saveAttachmentMetadata(Attachment attachment, String fileId, 
                                       String vectorStoreFileId, String chatbotId, String sessionId,
                                       String vectorStoreId) {
        
        long uploadedAt = System.currentTimeMillis();
        
        Document metadata = new Document()
                // OpenAI identifiers (for reference) - USE THESE TO QUERY OPENAI
                .append("fileId", fileId)
                .append("vectorStoreId", vectorStoreId)
                .append("vectorStoreFileId", vectorStoreFileId)
                
                // Context information
                .append("chatbotId", chatbotId)
                .append("sessionId", sessionId)
                
                // Original attachment information
                .append("originalName", attachment.getName())
                .append("mimeType", attachment.getMimeType())
                .append("fileSize", attachment.getSize())
                
                // ✨ CUSTOM METADATA - Store any additional info here
                .append("metadata", new Document()
                        .append("uploadedAt", new Date(uploadedAt))
                        .append("fileName", attachment.getName())
                        .append("fileType", attachment.getMimeType())
                        .append("size", attachment.getSize())
                        .append("status", "stored")
                )
                
                // Timestamps
                .append("uploadedAt", uploadedAt)
                .append("createdAt", new Date(uploadedAt))
                
                // Status and version
                .append("status", "stored")
                .append("source", "openai_vector_store")
                .append("version", 1);
        
        try {
            // Save to MongoDB collection
            String collectionName = "attachments_" + chatbotId;
            mongoTemplate.getCollection(collectionName).insertOne(metadata);
            
            log.info("Attachment metadata saved to MongoDB: collection={}, fileId={}", 
                    collectionName, fileId);
            
        } catch (Exception e) {
            log.warn("Failed to save metadata to MongoDB (non-critical): {}", e.getMessage());
            // Non-critical: logging only, don't fail the workflow
        }
    }
    
    /**
     * Delete temporary file from disk
     */
    private void deleteTemporaryFile(String filePath) throws IOException {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            log.debug("Temporary file deleted: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to delete temporary file: {}", filePath, e);
            throw e;
        }
    }
    
    /**
     * Validate MIME type against whitelist
     */
    private boolean isAllowedMimeType(String mimeType) {
        Set<String> allowed = new HashSet<>(Arrays.asList(
            // Documents
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/json",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // .docx
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",         // .xlsx
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
            // Images
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml"
        ));
        return allowed.contains(mimeType);
    }
    
    /**
     * Sanitize filename
     */
    private String sanitizeFilename(String filename) {
        String sanitized = filename.replaceAll("[/\\\\]", "_");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "");
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        return sanitized.isEmpty() ? "attachment_" + System.currentTimeMillis() : sanitized;
    }
    
    /**
     * Result object containing both vectorStoreId and vectorStoreFileId
     * to be passed to N8N webhook
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AttachmentSaveResult {
        private String vectorStoreId;      // vs_* - The vector store container ID
        private String vectorStoreFileId;  // vs_* - The file within the vector store
    }
}
