package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationStats {
    private long totalConversations;
    private long conversationsToday;
    private long conversationsThisWeek;
    private long conversationsThisMonth;
    private double averageMessagesPerConversation;
    private long longestConversation;
    private Map<String, Long> conversationsByMode;
    private long anonymousConversations;
    private long authenticatedConversations;
}

