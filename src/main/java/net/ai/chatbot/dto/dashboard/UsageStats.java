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
public class UsageStats {
    private long totalMessages;
    private long messagesToday;
    private long messagesThisWeek;
    private long messagesThisMonth;
    private double averageMessagesPerDay;
    private long peakMessagesInDay;
    private Map<String, Long> messagesByHour;
    private long totalUsers;
    private long activeUsersToday;
    private long activeUsersThisWeek;
    private long activeUsersThisMonth;
}

