package net.ai.chatbot.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for interacting with Supabase Storage API.
 * Configuration (url, service-role-key) is provided by config server.
 * Handles file uploads and deletions.
 */
@Service
@Slf4j
public class SupabaseStorageService {

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key:}")
    private String serviceRoleKey;

    @Value("${supabase.bucket:social-media-assets}")
    private String bucket;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Validate that Supabase configuration is present and properly formatted.
     * Throws exception if config is missing or invalid.
     */
    private void validateConfig() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new IllegalStateException(
                "Supabase URL is not configured. Check config server settings. " +
                "Expected property: supabase.url"
            );
        }
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalStateException(
                "Supabase service role key is not configured. Check config server settings. " +
                "Expected property: supabase.service-role-key"
            );
        }
        
        // Validate service role key format (should be long JWT-like string)
        if (serviceRoleKey.length() < 100) {
            log.warn(
                "Supabase service role key appears too short ({} chars). " +
                "This might be the wrong key. Ensure you're using the service_role key, not anon key. " +
                "Found in Supabase Dashboard → Settings → API → service_role",
                serviceRoleKey.length()
            );
        }
        
        // Check for common issues
        if (serviceRoleKey.contains(" ") || serviceRoleKey.contains("\n") || serviceRoleKey.contains("\t")) {
            throw new IllegalStateException(
                "Supabase service role key contains whitespace. " +
                "This is usually a copy-paste error. Remove any spaces, tabs, or newlines."
            );
        }
    }

    /**
     * Upload bytes to Supabase Storage.
     * Returns the public CDN URL.
     *
     * @param objectPath Path within the bucket (e.g., social-posts/user@example.com/123_photo.jpg)
     * @param bytes File content
     * @param contentType MIME type
     * @return Public CDN URL
     */
    public String upload(String objectPath, byte[] bytes, String contentType) {
        validateConfig();

        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.set("Content-Type", contentType != null ? contentType : "application/octet-stream");
        headers.set("x-upsert", "false"); // Don't overwrite existing files

        HttpEntity<byte[]> request = new HttpEntity<>(bytes, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Supabase upload failed: " + response.getBody());
            }

            // Construct public URL
            String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
            log.info("Uploaded to Supabase: {}", publicUrl);
            return publicUrl;

        } catch (Exception e) {
            log.error("Failed to upload to Supabase: {}", e.getMessage(), e);
            
            // Provide specific troubleshooting for common errors
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Invalid Compact JWS")) {
                throw new RuntimeException(
                    "Supabase service role key is invalid. " +
                    "This usually means: (1) Wrong key (using anon instead of service_role), " +
                    "(2) Key was corrupted in copy-paste, (3) Key has changed. " +
                    "Get the service_role key from Supabase Dashboard → Settings → API → service_role",
                    e
                );
            } else if (errorMsg != null && errorMsg.contains("Unauthorized")) {
                throw new RuntimeException(
                    "Supabase returned Unauthorized (403). " +
                    "Verify the service role key is correct and valid at Supabase Dashboard → Settings → API",
                    e
                );
            } else if (errorMsg != null && errorMsg.contains("Unable to connect")) {
                throw new RuntimeException(
                    "Failed to connect to Supabase. Check: (1) Supabase URL is correct, " +
                    "(2) Internet connectivity, (3) Supabase project is active (not paused)",
                    e
                );
            }
            
            throw new RuntimeException("Supabase upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file from Supabase Storage.
     *
     * @param objectPath Path within the bucket
     */
    public void delete(String objectPath) {
        validateConfig();

        String url = supabaseUrl + "/storage/v1/object/" + bucket;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Supabase expects a JSON body with "prefixes" array
        String body = "{\"prefixes\": [\"" + objectPath + "\"]}";

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
            log.info("Deleted from Supabase: {}", objectPath);

        } catch (Exception e) {
            log.error("Failed to delete from Supabase: {}", e.getMessage(), e);
            // Don't throw - gracefully handle Supabase deletion failures
        }
    }
}
