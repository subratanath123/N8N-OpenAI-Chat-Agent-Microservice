package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {
    @Id
    private String id;
    private String chatbotId;
    private String name;           // Original filename
    private long size;             // File size in bytes
    private long length;             // File size in bytes
    private String type;           // MIME type (e.g., "application/pdf")
    private Date uploadedAt;
    private byte[] data;           // Base64 encoded content
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
