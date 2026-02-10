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

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Controller for file upload/download operations
 */
@RestController
@RequestMapping("/api/attachments")
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@Slf4j
public class AttachmentDownloadController {

    @Autowired
    private AttachmentStorageService attachmentStorageService;

    /**
     * Upload file and get fileId
     */
    @PostMapping("/upload")
    public ResponseEntity<AttachmentStorageResult> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam String chatbotId,
            @RequestParam String sessionId) throws Exception {

        log.info("Upload: {}, size: {}", file.getOriginalFilename(), file.getSize());

        Attachment attachment = Attachment.builder()
                .name(file.getOriginalFilename())
                .chatbotId(chatbotId)
                .type(file.getContentType())
                .size(file.getSize())
                .data(file.getBytes())
                .uploadedAt(new Date())
                .build();

        AttachmentStorageResult result = attachmentStorageService.storeAttachmentInMongoDB(attachment, chatbotId);

        log.info("Uploaded: {}", result.getFileId());

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Download file by fileId
     */
    @GetMapping("/download/{fileId}/{chatbotId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId,
                                               @PathVariable String chatbotId) {

        log.info("Download: {}", fileId);

        FileMetadata metadata = attachmentStorageService.getFileMetadata(fileId, chatbotId);
        byte[] content = attachmentStorageService.getFileContent(fileId, chatbotId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, metadata.getMimeType())
                .body(content);
    }

    /**
     * Get file metadata
     */
    @GetMapping("/metadata/{fileId}/{chatbotId}")
    public ResponseEntity<FileMetadata> getFileMetadata(
            @PathVariable String fileId,
            @PathVariable String chatbotId) throws Exception {

        return ResponseEntity.ok(
                attachmentStorageService.getFileMetadata(fileId, chatbotId)
        );
    }

    /**
     * List all attachments for chatbot
     */
    @GetMapping("/list/{chatbotId}")
    public ResponseEntity<Map<String, Object>> listAttachments(@PathVariable String chatbotId) throws Exception {

        List<FileMetadata> files = attachmentStorageService.listAttachments(chatbotId);

        return ResponseEntity.ok(Map.of(
                "chatbotId", chatbotId,
                "totalFiles", files.size(),
                "files", files
        ));
    }

    /**
     * Delete attachment
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteAttachment(
            @PathVariable String fileId,
            @RequestParam String chatbotId) throws Exception {

        log.info("Delete: {}", fileId);

        boolean deleted = attachmentStorageService.deleteAttachment(fileId, chatbotId);

        return ResponseEntity.ok(Map.of(
                "success", deleted,
                "message", deleted ? "File deleted" : "File not found"
        ));
    }
}
