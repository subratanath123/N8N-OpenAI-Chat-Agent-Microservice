package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.ai.chatbot.dto.n8n.FileAttachment;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private String role;
    private String message;
    private String sessionId;
    private String chatbotId;

    private String greetingMessage;
    private String instruction;
    private String fallbackMessage;
    private String restrictDataSource;

    // File attachments (already uploaded with fileIds)
    private List<FileAttachment> fileAttachments;  // Pre-uploaded file references

}

