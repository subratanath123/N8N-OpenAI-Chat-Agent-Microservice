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
    
    // Statistics (optional, included in list response)
    private Long totalConversations;
    private Long totalMessages;

    /** True if the current user may edit this chatbot (owner or team Admin/Editor). */
    private Boolean canConfigure;
}

