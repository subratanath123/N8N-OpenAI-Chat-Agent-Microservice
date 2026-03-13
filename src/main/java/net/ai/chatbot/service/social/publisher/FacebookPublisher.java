package net.ai.chatbot.service.social.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.social.MediaItem;
import net.ai.chatbot.service.AttachmentStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;

/**
 * Facebook Graph API publisher.
 * Supports both immediate and scheduled posts using Facebook's native scheduling.
 * Handles media attachments (photos, videos).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FacebookPublisher {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v18.0";
    
    private final WebClient.Builder webClientBuilder;
    private final AttachmentStorageService attachmentStorageService;

    /**
     * Publish post immediately to Facebook page.
     *
     * @param pageId           Facebook page ID
     * @param pageAccessToken  Page access token (decrypted)
     * @param content          Post content/message
     * @param media            Media items to attach
     * @param userId           User ID (for downloading media)
     * @return Facebook post ID
     */
    public String publishImmediately(String pageId, String pageAccessToken, String content, 
                                    List<MediaItem> media, String userId) {
        log.info("Publishing immediately to Facebook page: {} with {} media item(s)", 
                pageId, media != null ? media.size() : 0);
        return publish(pageId, pageAccessToken, content, media, userId, null);
    }

    /**
     * Schedule post to Facebook page using Facebook's native scheduling.
     * Facebook handles the timing - no cron job needed!
     *
     * @param pageId           Facebook page ID
     * @param pageAccessToken  Page access token (decrypted)
     * @param content          Post content/message
     * @param media            Media items to attach
     * @param userId           User ID (for downloading media)
     * @param scheduledAt      When to publish (Unix timestamp)
     * @return Facebook scheduled post ID
     */
    public String publishScheduled(String pageId, String pageAccessToken, String content, 
                                  List<MediaItem> media, String userId, Instant scheduledAt) {
        log.info("Scheduling post to Facebook page {} for {} with {} media item(s)", 
                pageId, scheduledAt, media != null ? media.size() : 0);
        return publish(pageId, pageAccessToken, content, media, userId, scheduledAt);
    }

    /**
     * Internal publish method that handles both immediate and scheduled posts with media.
     */
    private String publish(String pageId, String pageAccessToken, String content, 
                          List<MediaItem> media, String userId, Instant scheduledAt) {
        try {
            // Upload media items first (if any)
            List<String> attachedMediaIds = new ArrayList<>();
            if (media != null && !media.isEmpty()) {
                for (MediaItem mediaItem : media) {
                    try {
                        String fbMediaId = uploadMediaToFacebook(pageId, pageAccessToken, mediaItem);
                        if (fbMediaId != null) {
                            attachedMediaIds.add(fbMediaId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to upload media {} to Facebook: {}", 
                                mediaItem.getMediaId(), e.getMessage(), e);
                        // Continue with other media items
                    }
                }
            }

            // Create the post
            Map<String, Object> body = new HashMap<>();
            if (content != null && !content.trim().isEmpty()) {
                body.put("message", content);
            }
            body.put("access_token", pageAccessToken);

            // Attach media IDs
            if (!attachedMediaIds.isEmpty()) {
                List<Map<String, String>> attachedMedia = new ArrayList<>();
                for (String fbMediaId : attachedMediaIds) {
                    attachedMedia.add(Map.of("media_fbid", fbMediaId));
                }
                body.put("attached_media", attachedMedia);
            }

            if (scheduledAt != null) {
                // Facebook native scheduling
                body.put("published", false);
                body.put("scheduled_publish_time", scheduledAt.getEpochSecond());
            }

            String postId = webClientBuilder.build()
                    .post()
                    .uri(GRAPH_API_BASE + "/" + pageId + "/feed")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(FacebookPostResponse.class)
                    .map(response -> response.id())
                    .block();

            if (scheduledAt != null) {
                log.info("Facebook post scheduled successfully: {} (will publish at {}) with {} media", 
                        postId, scheduledAt, attachedMediaIds.size());
            } else {
                log.info("Facebook post published immediately: {} with {} media", 
                        postId, attachedMediaIds.size());
            }
            return postId;
            
        } catch (Exception e) {
            log.error("Failed to publish to Facebook page {}: {}", pageId, e.getMessage(), e);
            throw new PublishException("Facebook publish failed: " + e.getMessage(), e);
        }
    }

    /**
     * Upload a single media item to Facebook and return the Facebook media ID.
     */
    private String uploadMediaToFacebook(String pageId, String pageAccessToken, 
                                        MediaItem mediaItem) {
        try {
            log.info("Uploading media {} ({}) to Facebook page {}", 
                    mediaItem.getMediaId(), mediaItem.getMimeType(), pageId);

            // Download media from our storage
            byte[] mediaBytes = attachmentStorageService.getFileContent(mediaItem.getMediaId());
            if (mediaBytes == null || mediaBytes.length == 0) {
                log.error("Media file not found or empty: {}", mediaItem.getMediaId());
                return null;
            }

            log.info("Downloaded media {} - size: {} bytes", mediaItem.getMediaId(), mediaBytes.length);

            // Determine Facebook endpoint based on media type
            String endpoint;
            if (mediaItem.getMimeType().startsWith("image/")) {
                endpoint = "/" + pageId + "/photos";
            } else if (mediaItem.getMimeType().startsWith("video/")) {
                endpoint = "/" + pageId + "/videos";
            } else {
                log.warn("Unsupported media type for Facebook: {}", mediaItem.getMimeType());
                return null;
            }

            // Upload to Facebook using proper multipart format
            org.springframework.core.io.ByteArrayResource resource = 
                    new org.springframework.core.io.ByteArrayResource(mediaBytes) {
                @Override
                public String getFilename() {
                    return mediaItem.getFileName();
                }
            };

            log.info("Uploading to Facebook: {} with filename: {}", 
                    GRAPH_API_BASE + endpoint, mediaItem.getFileName());

            FacebookMediaResponse response = webClientBuilder.build()
                    .post()
                    .uri(GRAPH_API_BASE + endpoint)
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData("source", resource)
                            .with("access_token", pageAccessToken)
                            .with("published", "false"))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Facebook API error response: {}", errorBody);
                                        return clientResponse.createException();
                                    }))
                    .bodyToMono(FacebookMediaResponse.class)
                    .block();

            if (response != null && response.id() != null) {
                log.info("Media uploaded to Facebook successfully: {}", response.id());
                return response.id();
            } else {
                log.error("Facebook media upload response missing ID");
                return null;
            }

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("Facebook API error: {} - Response: {}", 
                    e.getMessage(), e.getResponseBodyAsString());
            throw new PublishException("Facebook media upload failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to upload media {} to Facebook: {}", mediaItem.getMediaId(), e.getMessage(), e);
            throw new PublishException("Facebook media upload failed: " + e.getMessage(), e);
        }
    }

    private record FacebookPostResponse(String id) {}
    private record FacebookMediaResponse(String id) {}

    public static class PublishException extends RuntimeException {
        public PublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
