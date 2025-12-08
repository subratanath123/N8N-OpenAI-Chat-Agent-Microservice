package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverallStats {
    private long totalChatBots;
    private long totalConversations;
    private long totalMessages;
    private long totalUsers;
    private long activeChatBots;
    private long activeConversationsToday;
    private long totalKnowledgeBases;
}

