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
    private String message;
    private List<Attachment> attachments;
    private String sessionId;
    private String chatbotId;

}

