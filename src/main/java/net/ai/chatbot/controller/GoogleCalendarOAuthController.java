package net.ai.chatbot.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.GoogleCalendarTokenDao;
import net.ai.chatbot.dto.googlecalendar.*;
import net.ai.chatbot.entity.GoogleCalendarToken;
import net.ai.chatbot.service.googlecalendar.ChatbotOwnershipService;
import net.ai.chatbot.service.googlecalendar.GoogleOAuthService;
import net.ai.chatbot.utils.AuthUtils;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * REST Controller for Google Calendar OAuth integration
 */
@RestController
@RequestMapping("/v1/api/chatbot/google-calendar")
@Slf4j
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
public class GoogleCalendarOAuthController {

    private final GoogleCalendarTokenDao tokenDao;
    private final EncryptionUtils encryptionUtils;
    private final ChatbotOwnershipService ownershipService;
    private final GoogleOAuthService oauthService;

    private static final SimpleDateFormat ISO_DATE_FORMAT;

    static {
        ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public GoogleCalendarOAuthController(
            GoogleCalendarTokenDao tokenDao,
            EncryptionUtils encryptionUtils,
            ChatbotOwnershipService ownershipService,
            GoogleOAuthService oauthService) {
        this.tokenDao = tokenDao;
        this.encryptionUtils = encryptionUtils;
        this.ownershipService = ownershipService;
        this.oauthService = oauthService;
    }

    /**
     * 1. Store Google Calendar OAuth tokens for a chatbot
     * POST /v1/api/chatbot/google-calendar/{chatbotId}
     */
    @PostMapping("/{chatbotId}")
    public ResponseEntity<?> storeTokens(
            @PathVariable String chatbotId,
            @Valid @RequestBody StoreTokensRequest request) {

        try {
            // Get authenticated user email
            String userEmail = AuthUtils.getEmail();
            if (userEmail == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.builder()
                                .success(false)
                                .error("Unauthorized")
                                .message("Missing or invalid authorization header")
                                .build());
            }

            // Verify chatbot ownership
            ownershipService.verifyOwnership(chatbotId, userEmail);

            // Calculate expiration date
            Date expiresAt = new Date(System.currentTimeMillis() + request.getExpiresIn() * 1000L);

            // Encrypt tokens
            String encryptedAccessToken = encryptionUtils.encrypt(request.getAccessToken());
            String encryptedRefreshToken = encryptionUtils.encrypt(request.getRefreshToken());

            // Check if token already exists
            GoogleCalendarToken token = tokenDao.findByChatbotId(chatbotId)
                    .orElse(GoogleCalendarToken.builder()
                            .chatbotId(chatbotId)
                            .createdBy(userEmail)
                            .createdAt(new Date())
                            .build());

            // Update token
            token.setAccessToken(encryptedAccessToken);
            token.setRefreshToken(encryptedRefreshToken);
            token.setExpiresAt(expiresAt);
            token.setTokenType(request.getTokenType() != null ? request.getTokenType() : "Bearer");
            token.setUpdatedAt(new Date());

            // Save to database
            tokenDao.save(token);

            log.info("Successfully stored Google Calendar tokens for chatbot: {}", chatbotId);

            return ResponseEntity.ok(StoreTokensResponse.builder()
                    .success(true)
                    .message("Google Calendar tokens stored successfully")
                    .chatbotId(chatbotId)
                    .build());

        } catch (IllegalArgumentException e) {
            log.error("Chatbot not found: {}", chatbotId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Not Found")
                            .message("Chatbot not found: " + chatbotId)
                            .build());
        } catch (SecurityException e) {
            log.error("Ownership verification failed for chatbot: {}", chatbotId, e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Forbidden")
                            .message("User does not own this chatbot")
                            .build());
        } catch (Exception e) {
            log.error("Error storing tokens for chatbot: {}", chatbotId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Internal Server Error")
                            .message("Failed to store tokens")
                            .build());
        }
    }

    /**
     * 2. Get Google Calendar connection status
     * GET /v1/api/chatbot/google-calendar/{chatbotId}
     */
    @GetMapping("/{chatbotId}")
    public ResponseEntity<?> getConnectionStatus(@PathVariable String chatbotId) {

        try {
            // Get authenticated user email
            String userEmail = AuthUtils.getEmail();
            if (userEmail == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.builder()
                                .success(false)
                                .error("Unauthorized")
                                .message("Missing or invalid authorization header")
                                .build());
            }

            // Verify chatbot ownership
            ownershipService.verifyOwnership(chatbotId, userEmail);

            // Check if token exists
            GoogleCalendarToken token = tokenDao.findByChatbotId(chatbotId).orElse(null);

            if (token == null) {
                return ResponseEntity.ok(ConnectionStatusResponse.builder()
                        .connected(false)
                        .chatbotId(chatbotId)
                        .build());
            }

            boolean isExpired = token.getExpiresAt().before(new Date());

            return ResponseEntity.ok(ConnectionStatusResponse.builder()
                    .connected(true)
                    .chatbotId(chatbotId)
                    .expiresAt(ISO_DATE_FORMAT.format(token.getExpiresAt()))
                    .isExpired(isExpired)
                    .build());

        } catch (IllegalArgumentException e) {
            log.error("Chatbot not found: {}", chatbotId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Not Found")
                            .message("Chatbot not found: " + chatbotId)
                            .build());
        } catch (SecurityException e) {
            log.error("Ownership verification failed for chatbot: {}", chatbotId, e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Forbidden")
                            .message("User does not own this chatbot")
                            .build());
        } catch (Exception e) {
            log.error("Error getting connection status for chatbot: {}", chatbotId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Internal Server Error")
                            .message("Failed to get connection status")
                            .build());
        }
    }

    /**
     * 3. Get access token (with automatic refresh if expired)
     * GET /v1/api/chatbot/google-calendar/{chatbotId}/tokens
     */
    @GetMapping("/{chatbotId}/tokens")
    public Mono<ResponseEntity<?>> getTokens(@PathVariable String chatbotId) {

        try {
            // Get authenticated user email
            String userEmail = AuthUtils.getEmail();
            if (userEmail == null) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.builder()
                                .success(false)
                                .error("Unauthorized")
                                .message("Missing or invalid authorization header")
                                .build()));
            }

            // Verify chatbot ownership
            ownershipService.verifyOwnership(chatbotId, userEmail);

            // Get token from database
            GoogleCalendarToken token = tokenDao.findByChatbotId(chatbotId)
                    .orElseThrow(() -> new IllegalArgumentException("Google Calendar tokens not found for this chatbot"));

            // Check if expired
            if (token.getExpiresAt().before(new Date())) {
                log.info("Access token expired for chatbot {}, attempting refresh", chatbotId);

                // Decrypt refresh token
                String decryptedRefreshToken = encryptionUtils.decrypt(token.getRefreshToken());

                // Refresh the token
                return oauthService.refreshAccessToken(decryptedRefreshToken)
                        .<ResponseEntity<?>>map(newTokens -> {
                            // Update in database
                            Date newExpiresAt = new Date(System.currentTimeMillis() + newTokens.expiresIn * 1000L);
                            String encryptedAccessToken = encryptionUtils.encrypt(newTokens.accessToken);

                            token.setAccessToken(encryptedAccessToken);
                            token.setExpiresAt(newExpiresAt);
                            token.setTokenType(newTokens.tokenType);
                            token.setUpdatedAt(new Date());
                            tokenDao.save(token);

                            log.info("Successfully refreshed access token for chatbot: {}", chatbotId);

                            return ResponseEntity.ok(GetTokensResponse.builder()
                                    .success(true)
                                    .accessToken(newTokens.accessToken)
                                    .expiresAt(ISO_DATE_FORMAT.format(newExpiresAt))
                                    .tokenType(newTokens.tokenType)
                                    .build());
                        })
                        .onErrorResume(e -> {
                            log.error("Failed to refresh token for chatbot: {}", chatbotId, e);
                            return Mono.just(ResponseEntity.status(HttpStatus.GONE)
                                    .body(ErrorResponse.builder()
                                            .success(false)
                                            .error("Token Expired")
                                            .message("Access token has expired and refresh failed. Please reconnect.")
                                            .build()));
                        });
            }

            // Token is still valid, decrypt and return
            String decryptedAccessToken = encryptionUtils.decrypt(token.getAccessToken());

            return Mono.just(ResponseEntity.ok(GetTokensResponse.builder()
                    .success(true)
                    .accessToken(decryptedAccessToken)
                    .expiresAt(ISO_DATE_FORMAT.format(token.getExpiresAt()))
                    .tokenType(token.getTokenType())
                    .build()));

        } catch (IllegalArgumentException e) {
            log.error("Chatbot or tokens not found: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Not Found")
                            .message(e.getMessage())
                            .build()));
        } catch (SecurityException e) {
            log.error("Ownership verification failed for chatbot: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Forbidden")
                            .message("User does not own this chatbot")
                            .build()));
        } catch (Exception e) {
            log.error("Error getting tokens for chatbot: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Internal Server Error")
                            .message("Failed to get tokens")
                            .build()));
        }
    }

    /**
     * 4. Manually refresh access token
     * POST /v1/api/chatbot/google-calendar/{chatbotId}/refresh
     */
    @PostMapping("/{chatbotId}/refresh")
    public Mono<ResponseEntity<?>> refreshToken(@PathVariable String chatbotId) {

        try {
            // Get authenticated user email
            String userEmail = AuthUtils.getEmail();
            if (userEmail == null) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.builder()
                                .success(false)
                                .error("Unauthorized")
                                .message("Missing or invalid authorization header")
                                .build()));
            }

            // Verify chatbot ownership
            ownershipService.verifyOwnership(chatbotId, userEmail);

            // Get token from database
            GoogleCalendarToken token = tokenDao.findByChatbotId(chatbotId)
                    .orElseThrow(() -> new IllegalArgumentException("Google Calendar tokens not found for this chatbot"));

            // Decrypt refresh token
            String decryptedRefreshToken = encryptionUtils.decrypt(token.getRefreshToken());

            // Refresh the token
            return oauthService.refreshAccessToken(decryptedRefreshToken)
                    .<ResponseEntity<?>>map(newTokens -> {
                        // Update in database
                        Date newExpiresAt = new Date(System.currentTimeMillis() + newTokens.expiresIn * 1000L);
                        String encryptedAccessToken = encryptionUtils.encrypt(newTokens.accessToken);

                        token.setAccessToken(encryptedAccessToken);
                        token.setExpiresAt(newExpiresAt);
                        token.setTokenType(newTokens.tokenType);
                        token.setUpdatedAt(new Date());
                        tokenDao.save(token);

                        log.info("Successfully refreshed access token for chatbot: {}", chatbotId);

                        return ResponseEntity.ok(RefreshTokenResponse.builder()
                                .success(true)
                                .accessToken(newTokens.accessToken)
                                .expiresIn(newTokens.expiresIn)
                                .expiresAt(ISO_DATE_FORMAT.format(newExpiresAt))
                                .tokenType(newTokens.tokenType)
                                .build());
                    })
                    .onErrorResume(e -> {
                        log.error("Failed to refresh token for chatbot: {}", chatbotId, e);
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.builder()
                                        .success(false)
                                        .error("Refresh Failed")
                                        .message("Failed to refresh token: " + e.getMessage())
                                        .build()));
                    });

        } catch (IllegalArgumentException e) {
            log.error("Chatbot or tokens not found: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Not Found")
                            .message(e.getMessage())
                            .build()));
        } catch (SecurityException e) {
            log.error("Ownership verification failed for chatbot: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Forbidden")
                            .message("User does not own this chatbot")
                            .build()));
        } catch (Exception e) {
            log.error("Error refreshing token for chatbot: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Internal Server Error")
                            .message("Failed to refresh token")
                            .build()));
        }
    }

    /**
     * 5. Disconnect Google Calendar (delete tokens)
     * DELETE /v1/api/chatbot/google-calendar/{chatbotId}
     */
    @DeleteMapping("/{chatbotId}")
    public Mono<ResponseEntity<?>> disconnect(@PathVariable String chatbotId) {

        try {
            // Get authenticated user email
            String userEmail = AuthUtils.getEmail();
            if (userEmail == null) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.builder()
                                .success(false)
                                .error("Unauthorized")
                                .message("Missing or invalid authorization header")
                                .build()));
            }

            // Verify chatbot ownership
            ownershipService.verifyOwnership(chatbotId, userEmail);

            // Get token from database
            GoogleCalendarToken token = tokenDao.findByChatbotId(chatbotId)
                    .orElseThrow(() -> new IllegalArgumentException("Google Calendar tokens not found for this chatbot"));

            // Decrypt access token for revocation
            String decryptedAccessToken = encryptionUtils.decrypt(token.getAccessToken());

            // Revoke token with Google (non-blocking, best effort)
            return oauthService.revokeToken(decryptedAccessToken)
                    .then(Mono.fromRunnable(() -> {
                        // Delete from database
                        tokenDao.deleteByChatbotId(chatbotId);
                        log.info("Successfully disconnected Google Calendar for chatbot: {}", chatbotId);
                    }))
                    .<ResponseEntity<?>>then(Mono.just(ResponseEntity.ok(DisconnectResponse.builder()
                            .success(true)
                            .message("Google Calendar disconnected successfully")
                            .chatbotId(chatbotId)
                            .build())))
                    .onErrorResume(e -> {
                        // Even if revocation fails, still delete from database
                        log.warn("Token revocation failed, but proceeding with deletion", e);
                        tokenDao.deleteByChatbotId(chatbotId);
                        return Mono.just(ResponseEntity.ok(DisconnectResponse.builder()
                                .success(true)
                                .message("Google Calendar disconnected successfully (revocation may have failed)")
                                .chatbotId(chatbotId)
                                .build()));
                    });

        } catch (IllegalArgumentException e) {
            log.error("Chatbot or tokens not found: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Not Found")
                            .message(e.getMessage())
                            .build()));
        } catch (SecurityException e) {
            log.error("Ownership verification failed for chatbot: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Forbidden")
                            .message("User does not own this chatbot")
                            .build()));
        } catch (Exception e) {
            log.error("Error disconnecting Google Calendar for chatbot: {}", chatbotId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .error("Internal Server Error")
                            .message("Failed to disconnect Google Calendar")
                            .build()));
        }
    }
}

