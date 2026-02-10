package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata about a stored file
 * Does not include binary content (for efficient queries)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {
    
    /** Unique file identifier */
    private String fileId;
    
    /** Original file name */
    private String fileName;
    
    /** MIME type of the file */
    private String mimeType;
    
    /** File size in bytes */
    private long fileSize;
    
    /** Timestamp when file was uploaded (in milliseconds) */
    private long uploadedAt;
    
    /** Status of the file (e.g., "stored", "deleted") */
    private String status;
    
    /**
     * Get human-readable file size
     */
    public String getFormattedFileSize() {
        if (fileSize <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(fileSize) / Math.log10(1024));
        return String.format("%.1f %s", fileSize / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}

