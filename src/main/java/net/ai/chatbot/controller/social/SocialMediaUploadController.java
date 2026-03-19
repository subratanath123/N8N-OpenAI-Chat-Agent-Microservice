package net.ai.chatbot.controller.social;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.SocialAssetDao;
import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.AttachmentStorageResult;
import net.ai.chatbot.dto.social.MediaItem;
import net.ai.chatbot.dto.social.MediaUploadResponse;
import net.ai.chatbot.entity.SocialAsset;
import net.ai.chatbot.service.AttachmentStorageService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Controller for uploading media files for social posts.
 * Endpoint: POST /v1/api/social-media/upload
 * 
 * Stores file content in Attachment collection (for backward compatibility)
 * and metadata in SocialAsset collection (user email based).
 */
@RestController
@RequestMapping("/v1/api/social-media")
@RequiredArgsConstructor
@Slf4j
public class SocialMediaUploadController {

    private final AttachmentStorageService attachmentStorageService;
    private final SocialAssetDao socialAssetDao;

    @Value("${app.base-url:http://api.jadeordersmedia.com}")
    private String baseUrl;

    /**
     * Upload media files for social posts.
     * Accepts multiple files or single file.
     * 
     * POST /v1/api/social-media/upload
     * 
     * Flow:
     * 1. Store file content in Attachment collection (via AttachmentStorageService)
     * 2. Save metadata in SocialAsset collection (with userEmail)
     * 3. Return MediaItem for frontend use
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose) {

        String userId = AuthUtils.getUserId();
        String userEmail = AuthUtils.getUserEmail();
        
        if (userId == null || userEmail == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Authentication required"
            ));
        }

        // Determine which files to process
        List<MultipartFile> filesToProcess = new ArrayList<>();
        if (files != null && files.length > 0) {
            for (MultipartFile f : files) {
                if (!f.isEmpty()) {
                    filesToProcess.add(f);
                }
            }
        } else if (file != null && !file.isEmpty()) {
            filesToProcess.add(file);
        }

        if (filesToProcess.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "Bad Request",
                    "message", "No files provided or files are empty"
            ));
        }

        List<MediaItem> items = new ArrayList<>();

        for (MultipartFile uploadedFile : filesToProcess) {
            try {
                String mimeType = uploadedFile.getContentType();
                if (mimeType == null || mimeType.isEmpty()) {
                    mimeType = "application/octet-stream";
                }

                // Validate MIME type
                if (!isValidMediaType(mimeType)) {
                    return ResponseEntity.status(415).body(Map.of(
                            "error", "Unsupported Media Type",
                            "message", "File type not supported: " + mimeType,
                            "fileName", uploadedFile.getOriginalFilename()
                    ));
                }

                // Check file size (e.g., 50MB limit)
                long maxSize = 50 * 1024 * 1024; // 50MB
                if (uploadedFile.getSize() > maxSize) {
                    return ResponseEntity.status(413).body(Map.of(
                            "error", "Payload Too Large",
                            "message", "File size exceeds 50MB limit",
                            "fileName", uploadedFile.getOriginalFilename()
                    ));
                }

                byte[] fileBytes = uploadedFile.getBytes();

                // STEP 1: Store file content in Attachment collection (backward compatible)
                Attachment attachment = Attachment.builder()
                        .name(uploadedFile.getOriginalFilename())
                        .chatbotId(userId) // Store userId for reference only (not for ownership)
                        .type(mimeType)
                        .size(uploadedFile.getSize())
                        .data(fileBytes)
                        .uploadedAt(new Date())
                        .build();

                AttachmentStorageResult result = attachmentStorageService.storeAttachmentInMongoDB(attachment, userId);
                String attachmentId = result.getFileId();

                // Extract image dimensions if image
                Integer width = null;
                Integer height = null;
                if (mimeType.startsWith("image/")) {
                    try {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(fileBytes));
                        if (img != null) {
                            width = img.getWidth();
                            height = img.getHeight();
                        }
                    } catch (Exception e) {
                        log.warn("Could not read image dimensions for {}: {}", uploadedFile.getOriginalFilename(), e.getMessage());
                    }
                }

                // Construct download URL
                String downloadUrl = baseUrl.replaceAll("/$", "") + 
                        "/v1/api/user/attachments/download/" + attachmentId;

                // STEP 2: Save metadata in SocialAsset collection (with userEmail for ownership)
                SocialAsset socialAsset = SocialAsset.builder()
                        .userEmail(userEmail) // Store userEmail for ownership verification
                        .attachmentId(attachmentId)
                        .fileName(uploadedFile.getOriginalFilename())
                        .mimeType(mimeType)
                        .sizeBytes(uploadedFile.getSize())
                        .downloadUrl(downloadUrl)
                        .width(width)
                        .height(height)
                        .createdAt(Instant.now())
                        .build();

                SocialAsset saved = socialAssetDao.save(socialAsset);

                // STEP 3: Build MediaItem for response
                MediaItem mediaItem = MediaItem.builder()
                        .mediaId(saved.getId()) // Use SocialAsset ID (not attachmentId)
                        .mediaUrl(downloadUrl)
                        .mimeType(mimeType)
                        .fileName(uploadedFile.getOriginalFilename())
                        .sizeBytes(uploadedFile.getSize())
                        .width(width)
                        .height(height)
                        .durationMs(null) // TODO: Extract video duration if needed
                        .thumbnailUrl(null) // TODO: Generate thumbnail for videos/PDFs if needed
                        .build();

                items.add(mediaItem);

                log.info("Social media asset uploaded for user {}: socialAssetId={}, attachmentId={}, fileName={}", 
                        userEmail, saved.getId(), attachmentId, mediaItem.getFileName());

            } catch (Exception e) {
                log.error("Error uploading file {}: {}", uploadedFile.getOriginalFilename(), e.getMessage(), e);
                return ResponseEntity.status(500).body(Map.of(
                        "error", "Internal Server Error",
                        "message", "Failed to upload file: " + uploadedFile.getOriginalFilename(),
                        "details", e.getMessage()
                ));
            }
        }

        MediaUploadResponse response = MediaUploadResponse.builder()
                .items(items)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Validate if MIME type is supported for social media.
     */
    private boolean isValidMediaType(String mimeType) {
        return mimeType.startsWith("image/") ||
               mimeType.startsWith("video/") ||
               "application/pdf".equals(mimeType);
    }
}
