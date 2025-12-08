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
public class ChatBotStats {
    private long totalChatBots;
    private Map<String, Long> chatBotsByStatus;
    private long chatBotsCreatedToday;
    private long chatBotsCreatedThisWeek;
    private long chatBotsCreatedThisMonth;
    private double averageChatBotsPerUser;
    private Map<String, Long> chatBotsByDataSource;
}

