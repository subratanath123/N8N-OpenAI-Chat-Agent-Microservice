package net.ai.chatbot.controller.mediaasset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.mediaasset.DeleteResponse;
import net.ai.chatbot.dto.mediaasset.ListAssetsResponse;
import net.ai.chatbot.dto.mediaasset.UploadResponse;
import net.ai.chatbot.service.mediaasset.MediaAssetService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Media Assets API - Backend-owned Supabase uploads.
 * 
 * All endpoints require JWT authentication.
 * Files are uploaded to Supabase by the backend, not the frontend.
 * 
 * Base URL: /v1/api/assets
 */
@RestController
@RequestMapping("/v1/api/assets")
@RequiredArgsConstructor
@Slf4j
public class MediaAssetController {

    private final MediaAssetService mediaAssetService;

    /**
     * Upload one or more files.
     * Backend uploads to Supabase and saves metadata to DB.
     * 
     * POST /v1/api/assets/upload
     * Content-Type: multipart/form-data
     * 
     * Form field: files (one or more files)
     * 
     * Example cURL:
     * curl -X POST "http://localhost:8080/v1/api/assets/upload" \
     *   -H "Authorization: Bearer <jwt>" \
     *   -F "files=@photo.jpg" \
     *   -F "files=@video.mp4"
     * 
     * Response:
     * {
     *   "uploaded": [
     *     {
     *       "id": "550e8400-...",
     *       "fileName": "photo.jpg",
     *       "mimeType": "image/jpeg",
     *       "sizeBytes": 204800,
     *       "supabaseUrl": "https://xxx.supabase.co/.../photo.jpg",
     *       "objectPath": "social-posts/user@example.com/123_photo.jpg",
     *       "createdAt": "2024-01-15T10:00:00Z",
     *       "tags": []
     *     }
     *   ],
     *   "failed": []
     * }
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("files") List<MultipartFile> files) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized upload attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("No files provided");
        }

        UploadResponse response = mediaAssetService.uploadAll(userEmail, files);
        return ResponseEntity.ok(response);
    }

    /**
     * List assets for the authenticated user.
     * 
     * GET /v1/api/assets?type=image&search=photo&limit=100&offset=0
     * 
     * Query parameters:
     * - type: Filter by MIME type prefix (image, video)
     * - search: Case-insensitive filename contains
     * - limit: Max items to return (default 100, max 200)
     * - offset: Pagination offset (default 0)
     * 
     * Response:
     * {
     *   "assets": [...],
     *   "total": 42
     * }
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized list attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        ListAssetsResponse response = mediaAssetService.list(userEmail, type, search, limit, offset);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single asset by ID.
     * 
     * GET /v1/api/assets/{id}
     * 
     * Response: Same shape as a single item from list endpoint
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAsset(@PathVariable String id) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized get asset attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        ListAssetsResponse.AssetDto asset = mediaAssetService.getAsset(userEmail, id);
        return ResponseEntity.ok(asset);
    }

    /**
     * Delete an asset from both Supabase and database.
     * 
     * DELETE /v1/api/assets/{id}
     * 
     * Response:
     * {
     *   "success": true
     * }
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized delete attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        DeleteResponse response = mediaAssetService.delete(userEmail, id);
        return ResponseEntity.ok(response);
    }
}
