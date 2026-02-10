package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {
    private String name;           // Original filename
    private Long size;             // File size in bytes
    private String type;           // MIME type (e.g., "application/pdf")
    private String data;           // Base64 encoded content
    private String base64;         // Legacy support for base64 field
    
    /**
     * Get the file data, checking both data and base64 fields
     */
    public String getFileData() {
        return data != null ? data : base64;
    }
    
    /**
     * Get MIME type with sensible defaults
     */
    public String getMimeType() {
        if (type == null || type.isEmpty()) {
            return "application/octet-stream";
        }
        return type;
    }
}
