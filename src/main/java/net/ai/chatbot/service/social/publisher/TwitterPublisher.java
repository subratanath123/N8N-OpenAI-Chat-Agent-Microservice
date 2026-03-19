package net.ai.chatbot.service.social.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.social.MediaItem;
import net.ai.chatbot.service.AttachmentStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

/**
 * Twitter/X API v2 publisher.
 * Posts tweets immediately using OAuth 2.0 access tokens.
 * Handles media attachments (images, videos).
 * 
 * Note: Twitter's public API v2 does NOT support native scheduling.
 * Scheduled tweets require the Ads API (paid tier).
 * Use cron job for scheduling Twitter posts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TwitterPublisher {

    private static final String TWITTER_API_BASE = "https://api.twitter.com/2";
    private static final String TWITTER_UPLOAD_BASE = "https://upload.twitter.com/1.1";
    
    private final WebClient.Builder webClientBuilder;
    private final AttachmentStorageService attachmentStorageService;

    /**
     * Publish tweet immediately to Twitter/X.
     * No native scheduling available in public API v2.
     *
     * @param accessToken Twitter OAuth 2.0 access token (decrypted)
     * @param username    Twitter username (for logging)
     * @param content     Tweet content/text
     * @param media       Media items to attach
     * @param userId      User ID (for downloading media)
     * @return Twitter tweet ID
     */
    public String publishImmediately(String accessToken, String username, String content, 
                                    List<MediaItem> media, String userId) {
        log.info("Publishing to Twitter: @{} with {} media item(s)", 
                username, media != null ? media.size() : 0);

        try {
            // Upload media items first (if any)
            List<String> mediaIds = new ArrayList<>();
            if (media != null && !media.isEmpty()) {
                for (MediaItem mediaItem : media) {
                    try {
                        String twitterMediaId = uploadMediaToTwitter(accessToken, mediaItem);
                        if (twitterMediaId != null) {
                            mediaIds.add(twitterMediaId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to upload media {} to Twitter: {}", 
                                mediaItem.getMediaId(), e.getMessage(), e);
                        // Continue with other media items
                    }
                }
            }

            // Create tweet payload
            Map<String, Object> tweetData = new HashMap<>();
            if (content != null && !content.trim().isEmpty()) {
                tweetData.put("text", content);
            }
            
            // Add media IDs if any
            if (!mediaIds.isEmpty()) {
                tweetData.put("media", Map.of("media_ids", mediaIds));
            }

            // Post tweet
            String tweetId = webClientBuilder.build()
                    .post()
                    .uri(TWITTER_API_BASE + "/tweets")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(tweetData)
                    .retrieve()
                    .bodyToMono(TwitterPostResponse.class)
                    .map(response -> response.data().id())
                    .block();

            log.info("Twitter tweet published successfully: {} with {} media", tweetId, mediaIds.size());
            return tweetId;
            
        } catch (WebClientResponseException e) {
            log.error("Failed to publish to Twitter @{}: HTTP {} - {}", username, e.getStatusCode(), e.getMessage());
            
            // Handle 402 Payment Required specifically
            if (e.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                throw new PublishException(
                    "Twitter API access denied (402 Payment Required). " +
                    "Your Twitter Developer account needs to be upgraded to a paid tier (Basic or higher) " +
                    "to post tweets via the API. Please visit https://developer.twitter.com/en/portal/products " +
                    "to upgrade your access level.",
                    e
                );
            }
            
            // Handle other HTTP errors
            throw new PublishException(
                String.format("Twitter API error (%s): %s", e.getStatusCode(), e.getMessage()),
                e
            );
        } catch (Exception e) {
            log.error("Failed to publish to Twitter @{}: {}", username, e.getMessage(), e);
            throw new PublishException("Twitter publish failed: " + e.getMessage(), e);
        }
    }

    /**
     * Upload a single media item to Twitter and return the Twitter media ID.
     * Uses Twitter's media upload API (v1.1).
     * Handles both Attachment-based (SocialAsset) and CDN-based (MediaAsset) media.
     */
    private String uploadMediaToTwitter(String accessToken, MediaItem mediaItem) {
        try {
            log.info("Uploading media {} to Twitter", mediaItem.getMediaId());

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
                log.warn("Unsupported media type for Twitter: {}", mediaItem.getMimeType());
                return null;
            }

            // Upload to Twitter using media/upload endpoint
            // Note: This is a simplified version. For large files (>5MB), 
            // Twitter requires chunked upload (INIT, APPEND, FINALIZE)
            TwitterMediaResponse response = webClientBuilder.build()
                    .post()
                    .uri(TWITTER_UPLOAD_BASE + "/media/upload.json")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData("media", mediaBytes))
                    .retrieve()
                    .bodyToMono(TwitterMediaResponse.class)
                    .block();

            if (response != null && response.media_id_string() != null) {
                log.info("Media uploaded to Twitter successfully: {}", response.media_id_string());
                return response.media_id_string();
            } else {
                log.error("Twitter media upload response missing media_id");
                return null;
            }

        } catch (WebClientResponseException e) {
            log.error("Failed to upload media {} to Twitter: HTTP {} - {}", 
                    mediaItem.getMediaId(), e.getStatusCode(), e.getMessage());
            
            // Handle 402 Payment Required specifically
            if (e.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                throw new PublishException(
                    "Twitter API access denied (402 Payment Required). " +
                    "Your Twitter Developer account needs paid tier access for media uploads.",
                    e
                );
            }
            
            throw new PublishException(
                String.format("Twitter media upload error (%s): %s", e.getStatusCode(), e.getMessage()),
                e
            );
        } catch (Exception e) {
            log.error("Failed to upload media {} to Twitter: {}", mediaItem.getMediaId(), e.getMessage(), e);
            throw new PublishException("Twitter media upload failed: " + e.getMessage(), e);
        }
    }

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

    private record TwitterPostResponse(TweetData data) {}
    private record TweetData(String id, String text) {}
    private record TwitterMediaResponse(String media_id_string, long size) {}

    public static class PublishException extends RuntimeException {
        public PublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
