package net.ai.chatbot.dto.aichatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-chatbot statistics response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotStatsItemResponse {
    
    private String chatbotId;
    private Long totalConversations;
    private Long totalMessages;
}
