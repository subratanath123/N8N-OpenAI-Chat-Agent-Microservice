package net.ai.chatbot.controller.social;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.social.*;
import net.ai.chatbot.entity.social.SocialPost;
import net.ai.chatbot.service.AttachmentStorageService;
import net.ai.chatbot.service.social.SocialAccountService;
import net.ai.chatbot.service.social.publisher.SocialPostPublisher;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Social post scheduling API.
 */
@RestController
@RequestMapping("/v1/api/social-posts")
@RequiredArgsConstructor
@Slf4j
public class SocialPostController {

    private final SocialAccountService socialAccountService;
    private final SocialPostPublisher socialPostPublisher;
    private final AttachmentStorageService attachmentStorageService;

    /**
     * 6) Schedule Post
     * POST /v1/api/social-posts/schedule
     */
    @PostMapping("/schedule")
    public ResponseEntity<?> schedulePost(
            @Valid @RequestBody SchedulePostRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Authentication required"
            ));
        }
        
        // Verify ownership of all media items
        if (request.getMedia() != null && !request.getMedia().isEmpty()) {
            for (net.ai.chatbot.dto.social.MediaItem mediaItem : request.getMedia()) {
                String mediaId = mediaItem.getMediaId();
                if (mediaId == null || mediaId.trim().isEmpty()) {
                    return ResponseEntity.status(400).body(Map.of(
                            "error", "Bad Request",
                            "message", "mediaId is required for all media items"
                    ));
                }
                
                if (!attachmentStorageService.verifyOwnership(mediaId, userId)) {
                    log.warn("User {} attempted to use media {} they don't own", userId, mediaId);
                    return ResponseEntity.status(403).body(Map.of(
                            "error", "Forbidden",
                            "message", "You do not have permission to use media: " + mediaId
                    ));
                }
            }
        }
        
        try {
            // Save post to DB
            SchedulePostResponse response = socialAccountService.schedulePost(userId, request);
            
            // If immediate or Facebook, publish now
            if (response.getStatus().equals("pending_publish")) {
                try {
                    // Retrieve the saved post
                    SocialPost post = SocialPost.builder()
                            .id(response.getPostId())
                            .userId(userId)
                            .targetIds(request.getTargetIds())
                            .content(request.getContent())
                            .media(request.getMedia())
                            .status("pending_publish")
                            .scheduledAt(response.getScheduledAt())
                            .createdAt(java.time.Instant.now())
                            .build();
                    
                    boolean isImmediate = request.isImmediate();
                    SocialPostPublisher.PublishResult result = socialPostPublisher.publishPost(post, isImmediate);
                    
                    if (result.allTargetsSucceeded()) {
                        log.info("Post {} published successfully to all targets", post.getId());
                        response.setStatus("published");
                    } else {
                        log.warn("Post {} published with {} error(s)", post.getId(), result.errorCount());
                        response.setStatus("published_with_errors");
                    }
                } catch (Exception e) {
                    log.error("Failed to publish post {}: {}", response.getPostId(), e.getMessage(), e);
                    response.setStatus("publish_failed");
                }
            }
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error scheduling post for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to schedule post"
            ));
        }
    }

    /**
     * Get posts by date range (for calendar view)
     * GET /v1/api/social-posts?startDate=...&endDate=...&platform=...&status=...
     */
    @GetMapping
    public ResponseEntity<GetPostsResponse> getPosts(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status) {
        
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        GetPostsRequest request = new GetPostsRequest();
        request.setStartDate(java.time.Instant.parse(startDate));
        request.setEndDate(java.time.Instant.parse(endDate));
        request.setPlatform(platform);
        request.setStatus(status);
        
        return ResponseEntity.ok(socialAccountService.getPostsByDateRange(userId, request));
    }
}
