package net.ai.chatbot.dto.aichatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotCreationResponse {
    
    private String id;
    private String title;
    private String name;
    private Date createdAt;
    private String createdBy;
    private String status;
    private String message;
}

