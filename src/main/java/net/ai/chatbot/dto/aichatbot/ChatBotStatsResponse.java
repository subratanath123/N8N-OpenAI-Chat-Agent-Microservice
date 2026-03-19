package net.ai.chatbot.dto.aichatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated statistics for all chatbots
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotStatsResponse {
    
    private Long totalChatbots;
    private Long totalConversations;
    private Long totalMessages;
    private Long activeDomains;  // Count of ACTIVE chatbots
}
