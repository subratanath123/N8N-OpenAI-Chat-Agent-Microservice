package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.dashboard.*;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.ChatHistory;
import net.ai.chatbot.entity.KnowledgeBase;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DashboardService {

    private final MongoTemplate mongoTemplate;
    private final ChatBotDao chatBotDao;

    public DashboardService(MongoTemplate mongoTemplate, ChatBotDao chatBotDao) {
        this.mongoTemplate = mongoTemplate;
        this.chatBotDao = chatBotDao;
    }

    /**
     * Get comprehensive dashboard stats
     */
    public DashboardStatsResponse getDashboardStats() {
        log.info("Fetching dashboard stats for user: {}", AuthUtils.getEmail());

        OverallStats overallStats = getOverallStats();
        ChatBotStats chatBotStats = getChatBotStats();
        ConversationStats conversationStats = getConversationStats();
        UsageStats usageStats = getUsageStats();
        List<TimeSeriesData> usageOverTime = getUsageOverTime(30); // Last 30 days
        List<TopChatBot> topChatBots = getTopChatBots(10);
        List<UserActivity> topActiveUsers = getTopActiveUsers(10);

        return DashboardStatsResponse.builder()
                .overallStats(overallStats)
                .chatBotStats(chatBotStats)
                .conversationStats(conversationStats)
                .usageStats(usageStats)
                .usageOverTime(usageOverTime)
                .topChatBots(topChatBots)
                .topActiveUsers(topActiveUsers)
                .build();
    }

    /**
     * Get overall statistics
     */
    public OverallStats getOverallStats() {
        long totalChatBots = mongoTemplate.count(new Query(), ChatBot.class);
        long totalConversations = mongoTemplate.count(new Query(), ChatHistory.class);
        long totalMessages = getTotalMessagesCount();
        long totalUsers = mongoTemplate.count(new Query(), net.ai.chatbot.dto.User.class);
        long activeChatBots = getActiveChatBotsCount();
        long activeConversationsToday = getActiveConversationsToday();
        long totalKnowledgeBases = mongoTemplate.count(new Query(), KnowledgeBase.class);

        return OverallStats.builder()
                .totalChatBots(totalChatBots)
                .totalConversations(totalConversations)
                .totalMessages(totalMessages)
                .totalUsers(totalUsers)
                .activeChatBots(activeChatBots)
                .activeConversationsToday(activeConversationsToday)
                .totalKnowledgeBases(totalKnowledgeBases)
                .build();
    }

    /**
     * Get chatbot-specific statistics
     */
    public ChatBotStats getChatBotStats() {
        long totalChatBots = mongoTemplate.count(new Query(), ChatBot.class);
        
        // Chatbots by status
        Map<String, Long> chatBotsByStatus = getChatBotsByStatus();
        
        // Chatbots created today, this week, this month
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant weekAgo = today.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = today.minus(30, ChronoUnit.DAYS);
        
        long chatBotsCreatedToday = countChatBotsCreatedAfter(today);
        long chatBotsCreatedThisWeek = countChatBotsCreatedAfter(weekAgo);
        long chatBotsCreatedThisMonth = countChatBotsCreatedAfter(monthAgo);
        
        // Average chatbots per user
        double averageChatBotsPerUser = getAverageChatBotsPerUser();
        
        // Chatbots by data source
        Map<String, Long> chatBotsByDataSource = getChatBotsByDataSource();

        return ChatBotStats.builder()
                .totalChatBots(totalChatBots)
                .chatBotsByStatus(chatBotsByStatus)
                .chatBotsCreatedToday(chatBotsCreatedToday)
                .chatBotsCreatedThisWeek(chatBotsCreatedThisWeek)
                .chatBotsCreatedThisMonth(chatBotsCreatedThisMonth)
                .averageChatBotsPerUser(averageChatBotsPerUser)
                .chatBotsByDataSource(chatBotsByDataSource)
                .build();
    }

    /**
     * Get conversation statistics
     */
    public ConversationStats getConversationStats() {
        long totalConversations = mongoTemplate.count(new Query(), ChatHistory.class);
        
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant weekAgo = today.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = today.minus(30, ChronoUnit.DAYS);
        
        long conversationsToday = getConversationsCountAfter(today);
        long conversationsThisWeek = getConversationsCountAfter(weekAgo);
        long conversationsThisMonth = getConversationsCountAfter(monthAgo);
        
        double averageMessagesPerConversation = getAverageMessagesPerConversation();
        long longestConversation = getLongestConversation();
        
        Map<String, Long> conversationsByMode = getConversationsByMode();
        
        long anonymousConversations = getAnonymousConversationsCount();
        long authenticatedConversations = getAuthenticatedConversationsCount();

        return ConversationStats.builder()
                .totalConversations(totalConversations)
                .conversationsToday(conversationsToday)
                .conversationsThisWeek(conversationsThisWeek)
                .conversationsThisMonth(conversationsThisMonth)
                .averageMessagesPerConversation(averageMessagesPerConversation)
                .longestConversation(longestConversation)
                .conversationsByMode(conversationsByMode)
                .anonymousConversations(anonymousConversations)
                .authenticatedConversations(authenticatedConversations)
                .build();
    }

    /**
     * Get usage statistics
     */
    public UsageStats getUsageStats() {
        long totalMessages = getTotalMessagesCount();
        
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant weekAgo = today.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = today.minus(30, ChronoUnit.DAYS);
        
        long messagesToday = getMessagesCountAfter(today);
        long messagesThisWeek = getMessagesCountAfter(weekAgo);
        long messagesThisMonth = getMessagesCountAfter(monthAgo);
        
        double averageMessagesPerDay = getAverageMessagesPerDay(30);
        long peakMessagesInDay = getPeakMessagesInDay();
        
        Map<String, Long> messagesByHour = getMessagesByHour();
        
        long totalUsers = mongoTemplate.count(new Query(), net.ai.chatbot.dto.User.class);
        long activeUsersToday = getActiveUsersCountAfter(today);
        long activeUsersThisWeek = getActiveUsersCountAfter(weekAgo);
        long activeUsersThisMonth = getActiveUsersCountAfter(monthAgo);

        return UsageStats.builder()
                .totalMessages(totalMessages)
                .messagesToday(messagesToday)
                .messagesThisWeek(messagesThisWeek)
                .messagesThisMonth(messagesThisMonth)
                .averageMessagesPerDay(averageMessagesPerDay)
                .peakMessagesInDay(peakMessagesInDay)
                .messagesByHour(messagesByHour)
                .totalUsers(totalUsers)
                .activeUsersToday(activeUsersToday)
                .activeUsersThisWeek(activeUsersThisWeek)
                .activeUsersThisMonth(activeUsersThisMonth)
                .build();
    }

    /**
     * Get usage over time (time series data)
     */
    public List<TimeSeriesData> getUsageOverTime(int days) {
        Instant startDate = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(days, ChronoUnit.DAYS);
        
        // Aggregate conversations by date
        MatchOperation matchConversations = Aggregation.match(
                Criteria.where("createdAt").gte(startDate)
        );
        
        ProjectionOperation projectDate = Aggregation.project()
                .andExpression("dateToString('%Y-%m-%d', createdAt)").as("date");
        
        GroupOperation groupByDate = Aggregation.group("date")
                .count().as("conversations");
        
        Aggregation aggregation = Aggregation.newAggregation(
                matchConversations,
                projectDate,
                groupByDate
        );
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(
                aggregation,
                "n8n_chat_session_histories",
                Map.class
        ).getMappedResults();
        
        // Create a map of date to data
        Map<String, TimeSeriesData> dataMap = new HashMap<>();
        
        // Initialize all dates with zero values
        for (int i = 0; i < days; i++) {
            String date = LocalDate.now().minusDays(i).toString();
            dataMap.put(date, TimeSeriesData.builder()
                    .date(date)
                    .conversations(0)
                    .messages(0)
                    .users(0)
                    .chatBots(0)
                    .build());
        }
        
        // Fill in actual data
        for (Map<String, Object> result : results) {
            String date = (String) result.get("_id");
            long conversations = ((Number) result.get("conversations")).longValue();
            if (dataMap.containsKey(date)) {
                dataMap.get(date).setConversations(conversations);
            }
        }
        
        // Sort by date ascending
        return dataMap.values().stream()
                .sorted(Comparator.comparing(TimeSeriesData::getDate))
                .collect(Collectors.toList());
    }

    /**
     * Get top chatbots by activity
     */
    public List<TopChatBot> getTopChatBots(int limit) {
        // Aggregate conversations by chatbotId
        GroupOperation groupByChatBot = Aggregation.group("chatbotId")
                .count().as("conversationCount")
                .addToSet("conversationid").as("conversationIds");
        
        ProjectionOperation project = Aggregation.project("conversationCount", "conversationIds")
                .and("_id").as("chatbotId");
        
        SortOperation sort = Aggregation.sort(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "conversationCount"));
        
        LimitOperation limitOp = Aggregation.limit(limit);
        
        Aggregation aggregation = Aggregation.newAggregation(
                groupByChatBot,
                project,
                sort,
                limitOp
        );
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(
                aggregation,
                "n8n_chat_session_histories",
                Map.class
        ).getMappedResults();
        
        List<TopChatBot> topChatBots = new ArrayList<>();
        for (Map<String, Object> result : results) {
            String chatbotId = (String) result.get("chatbotId");
            long conversationCount = ((Number) result.get("conversationCount")).longValue();
            
            ChatBot chatBot = chatBotDao.findById(chatbotId).orElse(null);
            if (chatBot != null) {
                long messageCount = getMessageCountForChatBot(chatbotId);
                long uniqueUsers = getUniqueUsersForChatBot(chatbotId);
                
                topChatBots.add(TopChatBot.builder()
                        .chatBotId(chatbotId)
                        .chatBotName(chatBot.getName())
                        .chatBotTitle(chatBot.getTitle())
                        .conversationCount(conversationCount)
                        .messageCount(messageCount)
                        .uniqueUsers(uniqueUsers)
                        .status(chatBot.getStatus())
                        .createdBy(chatBot.getCreatedBy())
                        .build());
            }
        }
        
        return topChatBots;
    }

    /**
     * Get top active users
     */
    public List<UserActivity> getTopActiveUsers(int limit) {
        // Aggregate by email from UserChatHistory
        GroupOperation groupByEmail = Aggregation.group("email")
                .count().as("conversationCount")
                .max("createdAt").as("lastActivity");
        
        ProjectionOperation project = Aggregation.project("conversationCount", "lastActivity")
                .and("_id").as("email");
        
        SortOperation sort = Aggregation.sort(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "conversationCount"));
        
        LimitOperation limitOp = Aggregation.limit(limit);
        
        Aggregation aggregation = Aggregation.newAggregation(
                groupByEmail,
                project,
                sort,
                limitOp
        );
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(
                aggregation,
                "n8n_chat_session_histories",
                Map.class
        ).getMappedResults();
        
        List<UserActivity> userActivities = new ArrayList<>();
        for (Map<String, Object> result : results) {
            String email = (String) result.get("email");
            long conversationCount = ((Number) result.get("conversationCount")).longValue();
            Object lastActivityObj = result.get("lastActivity");
            String lastActivityDate = null;
            if (lastActivityObj != null) {
                if (lastActivityObj instanceof Instant) {
                    lastActivityDate = ((Instant) lastActivityObj).toString();
                } else if (lastActivityObj instanceof Date) {
                    lastActivityDate = ((Date) lastActivityObj).toInstant().toString();
                } else if (lastActivityObj instanceof String) {
                    lastActivityDate = (String) lastActivityObj;
                }
            }
            
            long messageCount = getMessageCountForUser(email);
            long chatBotsCreated = getChatBotsCreatedByUser(email);
            
            userActivities.add(UserActivity.builder()
                    .email(email)
                    .conversationCount(conversationCount)
                    .messageCount(messageCount)
                    .chatBotsCreated(chatBotsCreated)
                    .lastActivityDate(lastActivityDate)
                    .build());
        }
        
        return userActivities;
    }

    // Helper methods

    private long getTotalMessagesCount() {
        // Count messages in ChatHistory
        long chatHistoryMessages = mongoTemplate.findAll(ChatHistory.class).stream()
                .mapToLong(ch -> ch.getMessages() != null ? ch.getMessages().size() : 0)
                .sum();
        
        // Count messages in UserChatHistory (n8n sessions)
        long userChatHistoryMessages = mongoTemplate.count(new Query(), UserChatHistory.class);
        
        return chatHistoryMessages + userChatHistoryMessages;
    }

    private long getActiveChatBotsCount() {
        Query query = new Query(Criteria.where("status").in("COMPLETED", "TRAINING"));
        return mongoTemplate.count(query, ChatBot.class);
    }

    private long getActiveConversationsToday() {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Query query = new Query(Criteria.where("createdAt").gte(today));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private Map<String, Long> getChatBotsByStatus() {
        GroupOperation groupByStatus = Aggregation.group("status")
                .count().as("count");
        
        Aggregation aggregation = Aggregation.newAggregation(groupByStatus);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(
                aggregation,
                "chatbots",
                Map.class
        ).getMappedResults();
        
        Map<String, Long> statusMap = new HashMap<>();
        for (Map<String, Object> result : results) {
            String status = (String) result.get("_id");
            long count = ((Number) result.get("count")).longValue();
            statusMap.put(status != null ? status : "UNKNOWN", count);
        }
        
        return statusMap;
    }

    private long countChatBotsCreatedAfter(Instant date) {
        Date dateObj = Date.from(date);
        Query query = new Query(Criteria.where("createdAt").gte(dateObj));
        return mongoTemplate.count(query, ChatBot.class);
    }

    private double getAverageChatBotsPerUser() {
        GroupOperation groupByUser = Aggregation.group("createdBy")
                .count().as("count");
        
        Aggregation aggregation = Aggregation.newAggregation(groupByUser);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(
                aggregation,
                "chatbots",
                Map.class
        ).getMappedResults();
        
        if (results.isEmpty()) {
            return 0.0;
        }
        
        double total = results.stream()
                .mapToLong(r -> ((Number) r.get("count")).longValue())
                .sum();
        
        return total / results.size();
    }

    private Map<String, Long> getChatBotsByDataSource() {
        List<ChatBot> allChatBots = mongoTemplate.findAll(ChatBot.class);
        
        Map<String, Long> dataSourceMap = new HashMap<>();
        for (ChatBot chatBot : allChatBots) {
            String dataSource = chatBot.getSelectedDataSource();
            if (dataSource != null && !dataSource.isEmpty()) {
                dataSourceMap.put(dataSource, dataSourceMap.getOrDefault(dataSource, 0L) + 1);
            } else {
                dataSourceMap.put("NONE", dataSourceMap.getOrDefault("NONE", 0L) + 1);
            }
        }
        
        return dataSourceMap;
    }

    private long getConversationsCountAfter(Instant date) {
        Date dateObj = Date.from(date);
        Query query = new Query(Criteria.where("createdAt").gte(dateObj));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private double getAverageMessagesPerConversation() {
        List<ChatHistory> allChatHistories = mongoTemplate.findAll(ChatHistory.class);
        
        if (allChatHistories.isEmpty()) {
            return 0.0;
        }
        
        long totalMessages = allChatHistories.stream()
                .mapToLong(ch -> ch.getMessages() != null ? ch.getMessages().size() : 0)
                .sum();
        
        return (double) totalMessages / allChatHistories.size();
    }

    private long getLongestConversation() {
        return mongoTemplate.findAll(ChatHistory.class).stream()
                .mapToLong(ch -> ch.getMessages() != null ? ch.getMessages().size() : 0)
                .max()
                .orElse(0);
    }

    private Map<String, Long> getConversationsByMode() {
        GroupOperation groupByMode = Aggregation.group("mode")
                .count().as("count");
        
        Aggregation aggregation = Aggregation.newAggregation(groupByMode);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(
                aggregation,
                "n8n_chat_session_histories",
                Map.class
        ).getMappedResults();
        
        Map<String, Long> modeMap = new HashMap<>();
        for (Map<String, Object> result : results) {
            String mode = (String) result.get("_id");
            long count = ((Number) result.get("count")).longValue();
            modeMap.put(mode != null ? mode : "UNKNOWN", count);
        }
        
        return modeMap;
    }

    private long getAnonymousConversationsCount() {
        Query query = new Query(Criteria.where("isAnonymous").is(true));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private long getAuthenticatedConversationsCount() {
        Query query = new Query(Criteria.where("isAnonymous").is(false));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private long getMessagesCountAfter(Instant date) {
        Date dateObj = Date.from(date);
        Query query = new Query(Criteria.where("createdAt").gte(dateObj));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private double getAverageMessagesPerDay(int days) {
        Instant startDate = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(days, ChronoUnit.DAYS);
        Date dateObj = Date.from(startDate);
        Query query = new Query(Criteria.where("createdAt").gte(dateObj));
        long messages = mongoTemplate.count(query, UserChatHistory.class);
        return (double) messages / days;
    }

    private long getPeakMessagesInDay() {
        // Group by date and count messages
        ProjectionOperation projectDate = Aggregation.project()
                .andExpression("dateToString('%Y-%m-%d', createdAt)").as("date");
        
        GroupOperation groupByDate = Aggregation.group("date")
                .count().as("count");
        
        SortOperation sort = Aggregation.sort(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "count"));
        
        LimitOperation limit = Aggregation.limit(1);
        
        Aggregation aggregation = Aggregation.newAggregation(
                projectDate,
                groupByDate,
                sort,
                limit
        );
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(
                aggregation,
                "n8n_chat_session_histories",
                Map.class
        ).getMappedResults();
        
        if (results.isEmpty()) {
            return 0;
        }
        
        return ((Number) results.get(0).get("count")).longValue();
    }

    private Map<String, Long> getMessagesByHour() {
        ProjectionOperation projectHour = Aggregation.project()
                .andExpression("hour(createdAt)").as("hour");
        
        GroupOperation groupByHour = Aggregation.group("hour")
                .count().as("count");
        
        Aggregation aggregation = Aggregation.newAggregation(
                projectHour,
                groupByHour
        );
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) mongoTemplate.aggregate(
                aggregation,
                "n8n_chat_session_histories",
                Map.class
        ).getMappedResults();
        
        Map<String, Long> hourMap = new HashMap<>();
        for (Map<String, Object> result : results) {
            int hour = ((Number) result.get("_id")).intValue();
            long count = ((Number) result.get("count")).longValue();
            hourMap.put(String.valueOf(hour), count);
        }
        
        return hourMap;
    }

    private long getActiveUsersCountAfter(Instant date) {
        Date dateObj = Date.from(date);
        Query query = new Query(Criteria.where("createdAt").gte(dateObj));
        query.fields().include("email");
        
        List<UserChatHistory> histories = mongoTemplate.find(query, UserChatHistory.class);
        return histories.stream()
                .map(UserChatHistory::getEmail)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private long getMessageCountForChatBot(String chatbotId) {
        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private long getUniqueUsersForChatBot(String chatbotId) {
        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        query.fields().include("email");
        
        List<UserChatHistory> histories = mongoTemplate.find(query, UserChatHistory.class);
        return histories.stream()
                .map(UserChatHistory::getEmail)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private long getMessageCountForUser(String email) {
        Query query = new Query(Criteria.where("email").is(email));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private long getChatBotsCreatedByUser(String email) {
        Query query = new Query(Criteria.where("createdBy").is(email));
        return mongoTemplate.count(query, ChatBot.class);
    }
}

