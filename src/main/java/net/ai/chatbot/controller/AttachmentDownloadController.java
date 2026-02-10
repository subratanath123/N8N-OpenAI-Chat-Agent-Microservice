package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.AttachmentStorageResult;
import net.ai.chatbot.dto.FileMetadata;
import net.ai.chatbot.service.AttachmentStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for uploading and managing attachments stored in MongoDB
 * 
 * Endpoints:
 * - POST /api/attachments/upload - Upload file and get fileId ✨
 * - GET /api/attachments/download/{fileId} - Download file by fileId
 * - GET /api/attachments/metadata/{fileId} - Get file metadata
 * - GET /api/attachments/list/{chatbotId} - List all attachments for a chatbot
 * - DELETE /api/attachments/{fileId} - Delete attachment
 */
@RestController
@RequestMapping("/api/attachments")
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@Slf4j
public class AttachmentDownloadController {
    
    @Autowired
    private AttachmentStorageService attachmentStorageService;
    
    /**
     * Upload file to MongoDB and get fileId
     * 
     * ✨ NEW ENDPOINT ✨
     * 
     * Usage:
     * POST /api/attachments/upload
     * Content-Type: multipart/form-data
     * 
     * Form Parameters:
     * - file: The file to upload (required)
     * - chatbotId: The chatbot ID (required)
     * - sessionId: The session ID (required)
     * 
     * Example:
     * curl -X POST "http://localhost:8080/api/attachments/upload" \
     *   -F "file=@report.pdf" \
     *   -F "chatbotId=chatbot_123" \
     *   -F "sessionId=session_456"
     * 
     * Response:
     * {
     *   "fileId": "file_chatbot_123_session_456_report_1707385649123",
     *   "fileName": "report.pdf",
     *   "mimeType": "application/pdf",
     *   "fileSize": 256000,
     *   "downloadUrl": "http://localhost:8080/api/attachments/download/file_...?chatbotId=chatbot_123",
     *   "uploadedAt": 1707385649000,
     *   "status": "stored"
     * }
     * 
     * @param file the file to upload (multipart)
     * @param chatbotId the chatbot ID
     * @param sessionId the session ID
     * @return AttachmentStorageResult with fileId and downloadUrl
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam String chatbotId,
            @RequestParam String sessionId) {
        
        log.info("Upload request: fileName={}, chatbotId={}, sessionId={}, fileSize={}", 
                file.getOriginalFilename(), chatbotId, sessionId, file.getSize());
        
        try {
            // Validate inputs
            if (file.isEmpty()) {
                log.warn("Empty file received");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("File is empty"));
            }
            
            if (chatbotId == null || chatbotId.trim().isEmpty()) {
                log.warn("chatbotId is required");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("chatbotId is required"));
            }
            
            if (sessionId == null || sessionId.trim().isEmpty()) {
                log.warn("sessionId is required");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("sessionId is required"));
            }
            
            // Convert MultipartFile to Attachment DTO
            byte[] fileBytes = file.getBytes();
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            
            Attachment attachment = Attachment.builder()
                    .name(file.getOriginalFilename())
                    .type(file.getContentType())
                    .size(file.getSize())
                    .data(base64Data)
                    .build();
            
            log.debug("Attachment created: name={}, type={}, size={}", 
                    attachment.getName(), attachment.getMimeType(), attachment.getSize());
            
            // Store in MongoDB
            AttachmentStorageResult result = attachmentStorageService
                    .storeAttachmentInMongoDB(attachment, chatbotId, sessionId);
            
            log.info("File uploaded successfully: fileId={}, downloadUrl={}", 
                    result.getFileId(), result.getDownloadUrl());
            
            // Return result with fileId and download URL
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(result);
            
        } catch (IOException e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error uploading file: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during file upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Unexpected error: " + e.getMessage()));
        }
    }
    
    /**
     * Download file by fileId
     * 
     * Usage from N8N:
     * GET /api/attachments/download/file_chatbot_123_session_456_report_1707385649123?chatbotId=chatbot_123
     * 
     * @param fileId the file ID
     * @param chatbotId the chatbot ID
     * @return file content with appropriate headers
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> downloadFile(
            @PathVariable String fileId,
            @RequestParam String chatbotId) {
        
        log.info("Download request: fileId={}, chatbotId={}", fileId, chatbotId);
        
        try {
            // Get file metadata first
            FileMetadata metadata = attachmentStorageService.getFileMetadata(fileId, chatbotId);
            if (metadata == null) {
                log.warn("File not found for download: fileId={}", fileId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("File not found: " + fileId));
            }
            
            // Get file content
            byte[] fileContent = attachmentStorageService.getFileContent(fileId, chatbotId);
            if (fileContent == null) {
                log.warn("File content not found: fileId={}", fileId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("File content not found"));
            }
            
            // Return file with appropriate headers
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + metadata.getFileName() + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, metadata.getMimeType())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileContent.length))
                    .body(fileContent);
            
        } catch (Exception e) {
            log.error("Error downloading file: fileId={}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error downloading file: " + e.getMessage()));
        }
    }
    
    /**
     * Get file metadata (without content)
     * 
     * Usage:
     * GET /api/attachments/metadata/file_chatbot_123_session_456_report_1707385649123?chatbotId=chatbot_123
     * 
     * @param fileId the file ID
     * @param chatbotId the chatbot ID
     * @return file metadata
     */
    @GetMapping("/metadata/{fileId}")
    public ResponseEntity<?> getFileMetadata(
            @PathVariable String fileId,
            @RequestParam String chatbotId) {
        
        log.info("Metadata request: fileId={}, chatbotId={}", fileId, chatbotId);
        
        try {
            FileMetadata metadata = attachmentStorageService.getFileMetadata(fileId, chatbotId);
            
            if (metadata == null) {
                log.warn("File metadata not found: fileId={}", fileId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("File not found: " + fileId));
            }
            
            return ResponseEntity.ok(metadata);
            
        } catch (Exception e) {
            log.error("Error retrieving file metadata: fileId={}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error retrieving metadata: " + e.getMessage()));
        }
    }
    
    /**
     * List all attachments for a chatbot
     * 
     * Usage:
     * GET /api/attachments/list/chatbot_123
     * 
     * @param chatbotId the chatbot ID
     * @return list of file metadata
     */
    @GetMapping("/list/{chatbotId}")
    public ResponseEntity<?> listAttachments(@PathVariable String chatbotId) {
        
        log.info("List attachments request: chatbotId={}", chatbotId);
        
        try {
            List<FileMetadata> files = attachmentStorageService.listAttachments(chatbotId);
            
            return ResponseEntity.ok()
                    .body(Map.of(
                            "chatbotId", chatbotId,
                            "totalFiles", files.size(),
                            "files", files
                    ));
            
        } catch (Exception e) {
            log.error("Error listing attachments: chatbotId={}", chatbotId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error listing attachments: " + e.getMessage()));
        }
    }
    
    /**
     * Delete attachment
     * 
     * Usage:
     * DELETE /api/attachments/file_chatbot_123_session_456_report_1707385649123?chatbotId=chatbot_123
     * 
     * @param fileId the file ID
     * @param chatbotId the chatbot ID
     * @return success response
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteAttachment(
            @PathVariable String fileId,
            @RequestParam String chatbotId) {
        
        log.info("Delete request: fileId={}, chatbotId={}", fileId, chatbotId);
        
        try {
            boolean deleted = attachmentStorageService.deleteAttachment(fileId, chatbotId);
            
            if (deleted) {
                return ResponseEntity.ok(createSuccessResponse("File deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("File not found: " + fileId));
            }
            
        } catch (Exception e) {
            log.error("Error deleting attachment: fileId={}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error deleting file: " + e.getMessage()));
        }
    }
    
    /**
     * Helper to create error response
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    /**
     * Helper to create success response
     */
    private Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}

