package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of storing an attachment in MongoDB
 * Contains download link for N8N to access the file
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentStorageResult {
    
    /** Unique file identifier */
    private String fileId;
    
    /** Original file name */
    private String fileName;
    
    /** MIME type of the file */
    private String mimeType;
    
    /** File size in bytes */
    private long fileSize;
    
    /** Download URL to retrieve the file */
    private String downloadUrl;
    
    /** Timestamp when file was uploaded */
    private long uploadedAt;
    
    /** Status of the file storage */
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

