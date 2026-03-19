package net.ai.chatbot.dto.mediaasset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response for uploaded assets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    /**
     * Successfully uploaded assets
     */
    private List<AssetDto> uploaded;

    /**
     * Failed uploads with error messages
     */
    private List<FailedUpload> failed;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedUpload {
        private String fileName;
        private String error;
    }
}
