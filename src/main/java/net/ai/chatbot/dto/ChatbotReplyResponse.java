package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for chatbot reply API endpoint
 * Returned after successfully saving admin reply to conversation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotReplyResponse {
    
    private boolean success;
    private String messageId;
    private String conversationId;
    private String chatbotId;
    private String message;
    private String role;
    private long timestamp;
    private boolean savedToDatabase;
    
    // Error response fields (used when success = false)
    private String error;
    
    /**
     * Create success response
     */
    public static ChatbotReplyResponse success(String messageId, String conversationId, 
                                               String chatbotId, String message, 
                                               String role, long timestamp) {
        return ChatbotReplyResponse.builder()
                .success(true)
                .messageId(messageId)
                .conversationId(conversationId)
                .chatbotId(chatbotId)
                .message(message)
                .role(role)
                .timestamp(timestamp)
                .savedToDatabase(true)
                .build();
    }
    
    /**
     * Create error response
     */
    public static ChatbotReplyResponse error(String errorCode, String errorMessage, long timestamp) {
        return ChatbotReplyResponse.builder()
                .success(false)
                .error(errorCode)
                .message(errorMessage)
                .timestamp(timestamp)
                .savedToDatabase(false)
                .build();
    }
}

