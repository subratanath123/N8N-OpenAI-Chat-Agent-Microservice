package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private OverallStats overallStats;
    private ChatBotStats chatBotStats;
    private ConversationStats conversationStats;
    private UsageStats usageStats;
    private List<TimeSeriesData> usageOverTime;
    private List<TopChatBot> topChatBots;
    private List<UserActivity> topActiveUsers;
}

