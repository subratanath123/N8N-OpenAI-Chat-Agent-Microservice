package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "uploaded_files")
public class SecureFileUpload {

    @Id
    private String id;
    private String email;
    private String fileName;
    private String contentType;
    private byte[] data;

}

