package net.ai.chatbot.service.mediaasset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.MediaAssetDao;
import net.ai.chatbot.dto.mediaasset.DeleteResponse;
import net.ai.chatbot.dto.mediaasset.ListAssetsResponse;
import net.ai.chatbot.dto.mediaasset.UploadResponse;
import net.ai.chatbot.entity.MediaAsset;
import net.ai.chatbot.service.storage.SupabaseStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing media assets.
 * Handles upload to Supabase, database tracking, and deletion.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MediaAssetService {

    private final MediaAssetDao mediaAssetDao;
    private final SupabaseStorageService supabaseStorage;

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

    /**
     * Upload multiple files to Supabase and save metadata to database.
     * Returns list of successfully uploaded assets and any failures.
     */
    @Transactional
    public UploadResponse uploadAll(String userEmail, List<MultipartFile> files) {
        List<UploadResponse.AssetDto> uploaded = new ArrayList<>();
        List<UploadResponse.FailedUpload> failed = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                // Validate file size
                if (file.getSize() > MAX_FILE_SIZE) {
                    throw new IllegalArgumentException("File exceeds 50 MB limit");
                }

                // Validate not empty
                if (file.isEmpty()) {
                    throw new IllegalArgumentException("File is empty");
                }

                // Sanitize filename
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || originalFilename.isEmpty()) {
                    originalFilename = "unnamed-file";
                }

                String safeName = sanitizeFilename(originalFilename);

                // Generate object path: social-posts/{userEmail}/{timestamp}_{filename}
                String objectPath = "social-posts/" + userEmail + "/" 
                        + System.currentTimeMillis() + "_" + safeName;

                // Upload to Supabase
                String publicUrl = supabaseStorage.upload(
                        objectPath, 
                        file.getBytes(), 
                        file.getContentType()
                );

                // Save to database
                MediaAsset asset = MediaAsset.builder()
                        .id(UUID.randomUUID().toString())
                        .userEmail(userEmail)
                        .fileName(originalFilename)
                        .mimeType(file.getContentType())
                        .sizeBytes(file.getSize())
                        .supabaseUrl(publicUrl)
                        .objectPath(objectPath)
                        .createdAt(Instant.now())
                        .tags(new ArrayList<>())
                        .build();

                mediaAssetDao.save(asset);
                log.info("Asset uploaded for user {}: {} ({})", userEmail, originalFilename, asset.getId());

                // Add to successful uploads
                uploaded.add(toAssetDto(asset));

            } catch (Exception e) {
                log.error("Failed to upload file for user {}: {}", userEmail, e.getMessage(), e);
                failed.add(UploadResponse.FailedUpload.builder()
                        .fileName(file.getOriginalFilename())
                        .error(e.getMessage())
                        .build());
            }
        }

        return UploadResponse.builder()
                .uploaded(uploaded)
                .failed(failed)
                .build();
    }

    /**
     * List assets for a user with optional filtering.
     */
    public ListAssetsResponse list(String userEmail, String type, String search, int limit, int offset) {
        // Validate pagination params
        if (limit < 1 || limit > 200) limit = 100;
        if (offset < 0) offset = 0;

        int page = offset / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());

        Page<MediaAsset> assetPage;

        // Apply filters
        if (search != null && !search.trim().isEmpty()) {
            // Search by filename
            assetPage = mediaAssetDao.findByUserEmailAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
                    userEmail, search, pageable);
        } else if (type != null && !type.isEmpty()) {
            // Filter by type (image or video)
            String mimeTypePattern = "^" + type + "/.*";
            assetPage = mediaAssetDao.findByUserEmailAndMimeTypeRegex(
                    userEmail, mimeTypePattern, pageable);
        } else {
            // No filter
            assetPage = mediaAssetDao.findByUserEmailOrderByCreatedAtDesc(userEmail, pageable);
        }

        // Convert to DTOs
        List<ListAssetsResponse.AssetDto> assets = assetPage.getContent().stream()
                .map(this::toListAssetDto)
                .collect(Collectors.toList());

        // Get total count
        long total = mediaAssetDao.countByUserEmail(userEmail);

        return ListAssetsResponse.builder()
                .assets(assets)
                .total(total)
                .build();
    }

    /**
     * Get a single asset by ID (with ownership verification).
     */
    public ListAssetsResponse.AssetDto getAsset(String userEmail, String id) {
        MediaAsset asset = mediaAssetDao.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, 
                        "Asset not found or you don't have permission to access it"
                ));

        return toListAssetDto(asset);
    }

    /**
     * Delete asset from both Supabase and database.
     */
    @Transactional
    public DeleteResponse delete(String userEmail, String id) {
        MediaAsset asset = mediaAssetDao.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Asset not found or you don't have permission to delete it"
                ));

        // Delete from Supabase
        supabaseStorage.delete(asset.getObjectPath());

        // Delete from database
        mediaAssetDao.deleteByIdAndUserEmail(id, userEmail);

        log.info("Asset deleted for user {}: {} ({})", userEmail, asset.getFileName(), id);

        return DeleteResponse.builder()
                .success(true)
                .build();
    }

    /**
     * Count assets for a user.
     */
    public long count(String userEmail) {
        return mediaAssetDao.countByUserEmail(userEmail);
    }

    /**
     * Sanitize filename to remove unsafe characters.
     */
    private String sanitizeFilename(String filename) {
        return filename
                .replaceAll("[^\\w.\\-]", "_")  // Replace non-alphanumeric (except . and -) with _
                .replaceAll("_+", "_")           // Replace multiple underscores with single
                .toLowerCase();                   // Lowercase for consistency
    }

    /**
     * Convert MediaAsset to UploadResponse.AssetDto.
     */
    private UploadResponse.AssetDto toAssetDto(MediaAsset asset) {
        return UploadResponse.AssetDto.builder()
                .id(asset.getId())
                .fileName(asset.getFileName())
                .mimeType(asset.getMimeType())
                .sizeBytes(asset.getSizeBytes())
                .supabaseUrl(asset.getSupabaseUrl())
                .objectPath(asset.getObjectPath())
                .createdAt(asset.getCreatedAt())
                .tags(asset.getTags())
                .build();
    }

    /**
     * Convert MediaAsset to ListAssetsResponse.AssetDto.
     */
    private ListAssetsResponse.AssetDto toListAssetDto(MediaAsset asset) {
        return ListAssetsResponse.AssetDto.builder()
                .id(asset.getId())
                .fileName(asset.getFileName())
                .mimeType(asset.getMimeType())
                .sizeBytes(asset.getSizeBytes())
                .supabaseUrl(asset.getSupabaseUrl())
                .objectPath(asset.getObjectPath())
                .createdAt(asset.getCreatedAt())
                .tags(asset.getTags())
                .build();
    }
}
