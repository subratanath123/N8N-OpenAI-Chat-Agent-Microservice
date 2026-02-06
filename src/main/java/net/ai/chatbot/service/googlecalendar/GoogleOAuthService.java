package net.ai.chatbot.service.googlecalendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Service for Google OAuth operations (token refresh, revoke)
 */
@Service
@Slf4j
public class GoogleOAuthService {

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;

    public GoogleOAuthService(
            WebClient.Builder webClientBuilder,
            @Value("${google.oauth.client-id}") String clientId,
            @Value("${google.oauth.client-secret}") String clientSecret) {
        this.webClient = webClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Response DTO for token refresh
     */
    public static class RefreshTokenResult {
        public String accessToken;
        public int expiresIn;
        public String tokenType;

        public RefreshTokenResult(String accessToken, int expiresIn, String tokenType) {
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
            this.tokenType = tokenType;
        }
    }

    /**
     * Refresh an expired access token using the refresh token
     *
     * @param refreshToken The refresh token
     * @return New access token and expiration
     * @throws RuntimeException if refresh fails
     */
    public Mono<RefreshTokenResult> refreshAccessToken(String refreshToken) {
        log.info("Refreshing Google OAuth access token");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        return webClient.post()
                .uri(GOOGLE_TOKEN_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Failed to refresh token: {}", errorBody);
                                    return Mono.error(new RuntimeException("Failed to refresh token: " + errorBody));
                                })
                )
                .bodyToMono(String.class)
                .map(responseBody -> {
                    try {
                        JsonNode jsonNode = OBJECT_MAPPER.readTree(responseBody);
                        String accessToken = jsonNode.get("access_token").asText();
                        int expiresIn = jsonNode.get("expires_in").asInt();
                        String tokenType = jsonNode.has("token_type") 
                                ? jsonNode.get("token_type").asText() 
                                : "Bearer";

                        log.info("Successfully refreshed access token, expires in {} seconds", expiresIn);
                        return new RefreshTokenResult(accessToken, expiresIn, tokenType);
                    } catch (Exception e) {
                        log.error("Failed to parse refresh token response", e);
                        throw new RuntimeException("Failed to parse refresh token response", e);
                    }
                });
    }

    /**
     * Revoke an access token with Google
     *
     * @param accessToken The access token to revoke
     * @return Mono indicating completion
     */
    public Mono<Void> revokeToken(String accessToken) {
        log.info("Revoking Google OAuth access token");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("token", accessToken);

        return webClient.post()
                .uri(GOOGLE_REVOKE_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.warn("Failed to revoke token (non-fatal): {}", errorBody);
                                    // Don't throw error as revocation failure is non-fatal
                                    return Mono.empty();
                                })
                )
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Successfully revoked access token"))
                .onErrorResume(e -> {
                    log.warn("Error revoking token (continuing anyway): {}", e.getMessage());
                    return Mono.empty();
                });
    }
}

