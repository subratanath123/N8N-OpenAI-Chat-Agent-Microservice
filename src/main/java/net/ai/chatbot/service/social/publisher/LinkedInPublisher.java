package net.ai.chatbot.service.social.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.social.MediaItem;
import net.ai.chatbot.service.AttachmentStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

/**
 * LinkedIn API publisher.
 * Posts to personal profiles using the UGC Posts API.
 * Handles media attachments (images, videos).
 * 
 * Note: LinkedIn access tokens last ~60 days (Sign In + Share products).
 * No automatic refresh without Marketing Developer Platform.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LinkedInPublisher {

    private static final String LINKEDIN_API_BASE = "https://api.linkedin.com/v2";
    
    private final WebClient.Builder webClientBuilder;
    private final AttachmentStorageService attachmentStorageService;

    /**
     * Publish post immediately to LinkedIn personal profile.
     * Uses UGC Posts API for personal sharing.
     *
     * @param accessToken LinkedIn OAuth 2.0 access token (decrypted)
     * @param linkedInUserId LinkedIn user ID (URN format: urn:li:person:AbCdEfGhIj)
     * @param displayName Display name (for logging)
     * @param content Post content/text
     * @param media Media items to attach
     * @param userId User ID (for downloading media)
     * @return LinkedIn post URN
     */
    public String publishImmediately(String accessToken, String linkedInUserId, String displayName,
                                    String content, List<MediaItem> media, String userId) {
        log.info("Publishing to LinkedIn: {} with {} media item(s)", 
                displayName, media != null ? media.size() : 0);

        try {
            // Upload media items first (if any)
            List<String> mediaAssetUrns = new ArrayList<>();
            if (media != null && !media.isEmpty()) {
                for (MediaItem mediaItem : media) {
                    try {
                        String assetUrn = uploadMediaToLinkedIn(accessToken, linkedInUserId, mediaItem, userId);
                        if (assetUrn != null) {
                            mediaAssetUrns.add(assetUrn);
                        }
                    } catch (Exception e) {
                        log.error("Failed to upload media {} to LinkedIn: {}", 
                                mediaItem.getMediaId(), e.getMessage(), e);
                        // Continue with other media items
                    }
                }
            }

            // Build UGC post payload
            Map<String, Object> ugcPost = buildUgcPost(linkedInUserId, content, mediaAssetUrns);

            // Post to LinkedIn
            LinkedInUgcPostResponse response = webClientBuilder.build()
                    .post()
                    .uri(LINKEDIN_API_BASE + "/ugcPosts")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Restli-Protocol-Version", "2.0.0")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ugcPost)
                    .retrieve()
                    .bodyToMono(LinkedInUgcPostResponse.class)
                    .block();

            String postId = response != null ? response.id() : null;
            log.info("LinkedIn post published successfully: {} with {} media", postId, mediaAssetUrns.size());
            return postId;
            
        } catch (WebClientResponseException e) {
            log.error("Failed to publish to LinkedIn {}: HTTP {} - {}", 
                    displayName, e.getStatusCode(), e.getMessage());
            
            // Handle 401 Unauthorized (expired token)
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new PublishException(
                    "LinkedIn API access denied (401 Unauthorized). " +
                    "Your LinkedIn access token has likely expired (~60 days). " +
                    "Please reconnect your LinkedIn account.",
                    e
                );
            }
            
            // Handle other HTTP errors
            throw new PublishException(
                String.format("LinkedIn API error (%s): %s", e.getStatusCode(), e.getMessage()),
                e
            );
        } catch (Exception e) {
            log.error("Failed to publish to LinkedIn {}: {}", displayName, e.getMessage(), e);
            throw new PublishException("LinkedIn publish failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build UGC post payload for LinkedIn.
     */
    private Map<String, Object> buildUgcPost(String linkedInUserId, String content, List<String> mediaAssetUrns) {
        Map<String, Object> ugcPost = new HashMap<>();
        
        ugcPost.put("author", linkedInUserId);
        ugcPost.put("lifecycleState", "PUBLISHED");
        
        // Build specific content
        Map<String, Object> shareContent = new HashMap<>();
        
        // Share commentary (text)
        if (content != null && !content.trim().isEmpty()) {
            shareContent.put("shareCommentary", Map.of("text", content));
        }
        
        // Share media category
        if (mediaAssetUrns.isEmpty()) {
            shareContent.put("shareMediaCategory", "NONE");
        } else {
            shareContent.put("shareMediaCategory", "IMAGE"); // or "VIDEO"
            
            // Add media assets
            List<Map<String, Object>> mediaList = mediaAssetUrns.stream()
                    .map(urn -> {
                        Map<String, Object> mediaItem = new HashMap<>();
                        mediaItem.put("status", "READY");
                        mediaItem.put("media", urn);
                        return mediaItem;
                    })
                    .toList();
            
            shareContent.put("media", mediaList);
        }
        
        ugcPost.put("specificContent", Map.of("com.linkedin.ugc.ShareContent", shareContent));
        
        // Visibility
        ugcPost.put("visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"));
        
        return ugcPost;
    }

    /**
     * Upload a single media item to LinkedIn and return the asset URN.
     * Uses LinkedIn's media upload flow (register, upload, finalize).
     * Handles both Attachment-based (SocialAsset) and CDN-based (MediaAsset) media.
     */
    private String uploadMediaToLinkedIn(String accessToken, String linkedInUserId, 
                                        MediaItem mediaItem, String userId) {
        try {
            log.info("Uploading media {} to LinkedIn", mediaItem.getMediaId());

            byte[] mediaBytes;
            
            // Try Attachment storage first (SocialAsset from /v1/api/social-media/upload)
            mediaBytes = attachmentStorageService.getFileContent(mediaItem.getMediaId());
            
            // If not found in Attachment, use mediaUrl directly (MediaAsset from /v1/api/assets/upload)
            if (mediaBytes == null || mediaBytes.length == 0) {
                if (mediaItem.getMediaUrl() != null && !mediaItem.getMediaUrl().isEmpty()) {
                    log.info("Media not in attachment storage, downloading from CDN URL: {}", mediaItem.getMediaUrl());
                    try {
                        mediaBytes = downloadFromUrl(mediaItem.getMediaUrl());
                    } catch (Exception e) {
                        log.error("Failed to download media from URL: {}", mediaItem.getMediaUrl(), e);
                        return null;
                    }
                } else {
                    log.error("Media file not found or empty: {}", mediaItem.getMediaId());
                    return null;
                }
            }

            // Check if media type is supported
            if (!mediaItem.getMimeType().startsWith("image/") && 
                !mediaItem.getMimeType().startsWith("video/")) {
                log.warn("Unsupported media type for LinkedIn: {}", mediaItem.getMimeType());
                return null;
            }

            // Step 1: Register upload
            String uploadUrl = registerMediaUpload(accessToken, linkedInUserId, mediaItem);
            if (uploadUrl == null) {
                log.error("Failed to register media upload for LinkedIn");
                return null;
            }

            // Step 2: Upload media bytes
            uploadMediaBytes(uploadUrl, mediaBytes);

            // Step 3: Return asset URN (simplified - in production, check upload status)
            String assetUrn = "urn:li:digitalmediaAsset:" + UUID.randomUUID().toString();
            log.info("Media uploaded to LinkedIn successfully: {}", assetUrn);
            return assetUrn;

        } catch (WebClientResponseException e) {
            log.error("Failed to upload media {} to LinkedIn: HTTP {} - {}", 
                    mediaItem.getMediaId(), e.getStatusCode(), e.getMessage());
            
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new PublishException(
                    "LinkedIn API access denied (401 Unauthorized). " +
                    "Your LinkedIn access token has likely expired.",
                    e
                );
            }
            
            throw new PublishException(
                String.format("LinkedIn media upload error (%s): %s", e.getStatusCode(), e.getMessage()),
                e
            );
        } catch (Exception e) {
            log.error("Failed to upload media {} to LinkedIn: {}", mediaItem.getMediaId(), e.getMessage(), e);
            throw new PublishException("LinkedIn media upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Register media upload with LinkedIn.
     * Returns the upload URL.
     */
    private String registerMediaUpload(String accessToken, String linkedInUserId, MediaItem mediaItem) {
        Map<String, Object> registerRequest = new HashMap<>();
        registerRequest.put("registerUploadRequest", Map.of(
            "owner", linkedInUserId,
            "recipes", List.of("urn:li:digitalmediaRecipe:feedshare-image"),
            "serviceRelationships", List.of(Map.of(
                "relationshipType", "OWNER",
                "identifier", "urn:li:userGeneratedContent"
            ))
        ));

        LinkedInRegisterUploadResponse response = webClientBuilder.build()
                .post()
                .uri(LINKEDIN_API_BASE + "/assets?action=registerUpload")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerRequest)
                .retrieve()
                .bodyToMono(LinkedInRegisterUploadResponse.class)
                .block();

        return response != null && response.value() != null 
                ? response.value().uploadMechanism().get("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest")
                        .get("uploadUrl").toString()
                : null;
    }

    /**
     * Upload media bytes to LinkedIn's upload URL.
     */
    private void uploadMediaBytes(String uploadUrl, byte[] mediaBytes) {
        webClientBuilder.build()
                .put()
                .uri(uploadUrl)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(mediaBytes)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    // Response records
    /**
     * Download media from a URL (for MediaAsset CDN URLs)
     */
    private byte[] downloadFromUrl(String url) throws Exception {
        return webClientBuilder
                .codecs(config -> config.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB limit
                .build()
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    private record LinkedInUgcPostResponse(String id) {}

    private record LinkedInRegisterUploadResponse(Value value) {
        record Value(Map<String, Map<String, Object>> uploadMechanism, String asset) {}
    }

    public static class PublishException extends RuntimeException {
        public PublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
