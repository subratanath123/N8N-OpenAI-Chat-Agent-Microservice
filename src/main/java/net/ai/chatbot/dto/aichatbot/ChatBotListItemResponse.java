package net.ai.chatbot.dto.aichatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Enhanced response for chatbot list with statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotListItemResponse {
    
    private String id;
    private String title;
    private String name;
    private Date createdAt;
    private String createdBy;
    private String status;
    
    // Statistics
    private Long totalConversations;
    private Long totalMessages;
}
