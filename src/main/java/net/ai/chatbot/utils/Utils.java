package net.ai.chatbot.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class Utils {
    /**
     * Extracts the base URL (scheme + host + optional port) from a full URL.
     *
     * @param fullUrl the full URL string (e.g., "https://api.openai.com/v1/chat/completions")
     * @return the base URL (e.g., "https://api.openai.com")
     * @throws IllegalArgumentException if the input URL is malformed
     */
    public static String extractBaseUrl(String fullUrl) {
        try {
            URI uri = new URI(fullUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            int defaultPort = new URL(fullUrl).getDefaultPort();

            if (scheme == null || host == null) {
                throw new IllegalArgumentException("Invalid URL: " + fullUrl);
            }

            StringBuilder baseUrl = new StringBuilder();
            baseUrl.append(scheme).append("://").append(host);

            // Append non-default port if present
            if (port != -1 && port != defaultPort) {
                baseUrl.append(":").append(port);
            }

            return baseUrl.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract base URL from: " + fullUrl, e);
        }
    }

    // Sanitizes file path by removing all characters except letters, digits, underscores, hyphens, and slashes
    public static String sanitizePath(String path) {
        return path.replaceAll("[^a-zA-Z0-9/_-]", "_");
    }

    // Creates folder if it doesn't exist
    public static void createDirectoryIfNotExists(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("Created directory: " + path);
        } else {
            log.info("Directory already exists: " + path);
        }
    }
}
