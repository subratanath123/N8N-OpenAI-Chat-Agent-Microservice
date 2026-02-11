package net.ai.chatbot.dto.n8n;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * File Attachment Reference DTO
 * 
 * Represents a file that has already been uploaded and stored with a fileId.
 * Used when frontend sends pre-uploaded file references instead of raw file content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAttachment {
    
    private String fileId;                      // Unique file ID from storage
    private String fileName;                    // Original filename
    private String mimeType;                    // MIME type (e.g., "application/pdf", "image/png")
    private Long fileSize;                      // File size in bytes
    private String downloadUrl;                 // URL to download the file
    
    // Optional metadata
    private Long uploadedAt;                    // Upload timestamp (milliseconds)
    private String chatbotId;                   // Chatbot ID this file belongs to
    private String sessionId;                   // Session ID this file belongs to
}


