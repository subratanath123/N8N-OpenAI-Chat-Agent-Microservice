package net.ai.chatbot.dto.mediaasset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response for listing assets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListAssetsResponse {

    /**
     * List of assets
     */
    private List<AssetDto> assets;

    /**
     * Total count of assets for the user
     */
    private long total;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetDto {
        private String id;
        private String fileName;
        private String mimeType;
        private Long sizeBytes;
        private String supabaseUrl;
        private String objectPath;
        private Instant createdAt;
        private List<String> tags;
    }
}
