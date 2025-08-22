package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private String role;
    private String content;
    private List<Attachment> attachments;

    // Convenience constructor for backward compatibility
    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.attachments = null;
    }
}

