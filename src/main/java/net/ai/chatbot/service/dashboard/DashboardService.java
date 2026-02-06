package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.dashboard.*;
import net.ai.chatbot.entity.ChatBot;
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
     * Get overall statistics (filtered by current user)
     */
    public OverallStats getOverallStats() {
        String currentUserEmail = AuthUtils.getEmail();
        
        // Filter chatbots by current user
        Query chatBotQuery = new Query(Criteria.where("createdBy").is(currentUserEmail));
        long totalChatBots = mongoTemplate.count(chatBotQuery, ChatBot.class);
        
        // Get list of user's chatbot IDs
        List<String> userChatBotIds = getUserChatBotIds(currentUserEmail);
        
        // Filter conversations by user's chatbots
        Query conversationQuery = new Query(Criteria.where("chatbotId").in(userChatBotIds));
        long totalConversations = mongoTemplate.count(conversationQuery, UserChatHistory.class);
        
        long totalMessages = getTotalMessagesCount(userChatBotIds);
        long activeChatBots = getActiveChatBotsCount(currentUserEmail);
        long activeConversationsToday = getActiveConversationsToday(userChatBotIds);
        
        // Knowledge bases for user's chatbots
        long totalKnowledgeBases = 0;
        for (String chatbotId : userChatBotIds) {
            Query kbQuery = new Query();
            // Assuming knowledge bases are stored in chatbot-specific collections
            totalKnowledgeBases += mongoTemplate.count(kbQuery, "jade-ai-knowledgebase-" + chatbotId);
        }

        return OverallStats.builder()
                .totalChatBots(totalChatBots)
                .totalConversations(totalConversations)
                .totalMessages(totalMessages)
                .totalUsers(1) // Current user
                .activeChatBots(activeChatBots)
                .activeConversationsToday(activeConversationsToday)
                .totalKnowledgeBases(totalKnowledgeBases)
                .build();
    }

    /**
     * Get chatbot-specific statistics (filtered by current user)
     */
    public ChatBotStats getChatBotStats() {
        String currentUserEmail = AuthUtils.getEmail();
        
        Query userQuery = new Query(Criteria.where("createdBy").is(currentUserEmail));
        long totalChatBots = mongoTemplate.count(userQuery, ChatBot.class);
        
        // Chatbots by status (for current user)
        Map<String, Long> chatBotsByStatus = getChatBotsByStatus(currentUserEmail);
        
        // Chatbots created today, this week, this month
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant weekAgo = today.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = today.minus(30, ChronoUnit.DAYS);
        
        long chatBotsCreatedToday = countChatBotsCreatedAfter(today, currentUserEmail);
        long chatBotsCreatedThisWeek = countChatBotsCreatedAfter(weekAgo, currentUserEmail);
        long chatBotsCreatedThisMonth = countChatBotsCreatedAfter(monthAgo, currentUserEmail);
        
        // Average chatbots per user (just 1 for current user)
        double averageChatBotsPerUser = totalChatBots; // For single user, it's just their total
        
        // Chatbots by data source (for current user)
        Map<String, Long> chatBotsByDataSource = getChatBotsByDataSource(currentUserEmail);

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
     * Get conversation statistics (filtered by current user's chatbots)
     */
    public ConversationStats getConversationStats() {
        String currentUserEmail = AuthUtils.getEmail();
        List<String> userChatBotIds = getUserChatBotIds(currentUserEmail);
        
        if (userChatBotIds.isEmpty()) {
            return ConversationStats.builder()
                    .totalConversations(0).conversationsToday(0).conversationsThisWeek(0)
                    .conversationsThisMonth(0).averageMessagesPerConversation(0.0)
                    .longestConversation(0).conversationsByMode(new HashMap<>())
                    .anonymousConversations(0).authenticatedConversations(0)
                    .build();
        }
        
        Query conversationQuery = new Query(Criteria.where("chatbotId").in(userChatBotIds));
        long totalConversations = mongoTemplate.count(conversationQuery, UserChatHistory.class);
        
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant weekAgo = today.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = today.minus(30, ChronoUnit.DAYS);
        
        long conversationsToday = getConversationsCountAfter(today, userChatBotIds);
        long conversationsThisWeek = getConversationsCountAfter(weekAgo, userChatBotIds);
        long conversationsThisMonth = getConversationsCountAfter(monthAgo, userChatBotIds);
        
        double averageMessagesPerConversation = 1.0; // Each UserChatHistory entry is 1 message
        long longestConversation = 1;
        
        Map<String, Long> conversationsByMode = getConversationsByMode(userChatBotIds);
        
        long anonymousConversations = getAnonymousConversationsCount(userChatBotIds);
        long authenticatedConversations = getAuthenticatedConversationsCount(userChatBotIds);

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
     * Get usage statistics (filtered by current user's chatbots)
     */
    public UsageStats getUsageStats() {
        String currentUserEmail = AuthUtils.getEmail();
        List<String> userChatBotIds = getUserChatBotIds(currentUserEmail);
        
        if (userChatBotIds.isEmpty()) {
            return UsageStats.builder()
                    .totalMessages(0).messagesToday(0).messagesThisWeek(0).messagesThisMonth(0)
                    .averageMessagesPerDay(0.0).peakMessagesInDay(0).messagesByHour(new HashMap<>())
                    .totalUsers(0).activeUsersToday(0).activeUsersThisWeek(0).activeUsersThisMonth(0)
                    .build();
        }
        
        long totalMessages = getTotalMessagesCount(userChatBotIds);
        
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant weekAgo = today.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = today.minus(30, ChronoUnit.DAYS);
        
        long messagesToday = getMessagesCountAfter(today, userChatBotIds);
        long messagesThisWeek = getMessagesCountAfter(weekAgo, userChatBotIds);
        long messagesThisMonth = getMessagesCountAfter(monthAgo, userChatBotIds);
        
        double averageMessagesPerDay = getAverageMessagesPerDay(30, userChatBotIds);
        long peakMessagesInDay = getPeakMessagesInDay(userChatBotIds);
        
        Map<String, Long> messagesByHour = getMessagesByHour(userChatBotIds);
        
        long totalUsers = getUniqueUsersForChatBots(userChatBotIds);
        long activeUsersToday = getActiveUsersCountAfter(today, userChatBotIds);
        long activeUsersThisWeek = getActiveUsersCountAfter(weekAgo, userChatBotIds);
        long activeUsersThisMonth = getActiveUsersCountAfter(monthAgo, userChatBotIds);

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
     * Get usage over time (time series data) filtered by current user's chatbots
     */
    public List<TimeSeriesData> getUsageOverTime(int days) {
        String currentUserEmail = AuthUtils.getEmail();
        List<String> userChatBotIds = getUserChatBotIds(currentUserEmail);
        
        if (userChatBotIds.isEmpty()) {
            // Return empty data for all days
            Map<String, TimeSeriesData> dataMap = new HashMap<>();
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
            return dataMap.values().stream()
                    .sorted(Comparator.comparing(TimeSeriesData::getDate))
                    .collect(Collectors.toList());
        }
        
        Instant startDate = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(days, ChronoUnit.DAYS);
        
        // Aggregate conversations by date for user's chatbots
        MatchOperation matchConversations = Aggregation.match(
                Criteria.where("chatbotId").in(userChatBotIds)
                        .and("createdAt").gte(startDate)
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
     * Get top chatbots by activity (filtered by current user's chatbots)
     */
    public List<TopChatBot> getTopChatBots(int limit) {
        String currentUserEmail = AuthUtils.getEmail();
        List<String> userChatBotIds = getUserChatBotIds(currentUserEmail);
        
        if (userChatBotIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Aggregate conversations by chatbotId for user's chatbots only
        MatchOperation matchChatbots = Aggregation.match(Criteria.where("chatbotId").in(userChatBotIds));
        GroupOperation groupByChatBot = Aggregation.group("chatbotId")
                .count().as("conversationCount")
                .addToSet("conversationid").as("conversationIds");
        
        ProjectionOperation project = Aggregation.project("conversationCount", "conversationIds")
                .and("_id").as("chatbotId");
        
        SortOperation sort = Aggregation.sort(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "conversationCount"));
        
        LimitOperation limitOp = Aggregation.limit(limit);
        
        Aggregation aggregation = Aggregation.newAggregation(
                matchChatbots,
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
     * Get top active users (filtered by conversations on current user's chatbots)
     */
    public List<UserActivity> getTopActiveUsers(int limit) {
        String currentUserEmail = AuthUtils.getEmail();
        List<String> userChatBotIds = getUserChatBotIds(currentUserEmail);
        
        if (userChatBotIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Aggregate by email from UserChatHistory for user's chatbots only
        MatchOperation matchChatbots = Aggregation.match(Criteria.where("chatbotId").in(userChatBotIds));
        GroupOperation groupByEmail = Aggregation.group("email")
                .count().as("conversationCount")
                .max("createdAt").as("lastActivity");
        
        ProjectionOperation project = Aggregation.project("conversationCount", "lastActivity")
                .and("_id").as("email");
        
        SortOperation sort = Aggregation.sort(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "conversationCount"));
        
        LimitOperation limitOp = Aggregation.limit(limit);
        
        Aggregation aggregation = Aggregation.newAggregation(
                matchChatbots,
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
            
            long messageCount = getMessageCountForUser(email, userChatBotIds);
            
            userActivities.add(UserActivity.builder()
                    .email(email)
                    .conversationCount(conversationCount)
                    .messageCount(messageCount)
                    .chatBotsCreated(0) // Not relevant for users chatting with current user's bots
                    .lastActivityDate(lastActivityDate)
                    .build());
        }
        
        return userActivities;
    }

    // Helper methods

    /**
     * Get list of chatbot IDs created by the user
     */
    private List<String> getUserChatBotIds(String userEmail) {
        Query query = new Query(Criteria.where("createdBy").is(userEmail));
        query.fields().include("id");
        return mongoTemplate.find(query, ChatBot.class).stream()
                .map(ChatBot::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private long getTotalMessagesCount(List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) {
            return 0;
        }
        
        // Count messages in UserChatHistory (n8n sessions) for user's chatbots
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private long getActiveChatBotsCount(String userEmail) {
        Query query = new Query(Criteria.where("createdBy").is(userEmail)
                .and("status").in("COMPLETED", "TRAINING"));
        return mongoTemplate.count(query, ChatBot.class);
    }

    private long getActiveConversationsToday(List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) {
            return 0;
        }
        
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds)
                .and("createdAt").gte(today));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private Map<String, Long> getChatBotsByStatus(String userEmail) {
        MatchOperation matchUser = Aggregation.match(Criteria.where("createdBy").is(userEmail));
        GroupOperation groupByStatus = Aggregation.group("status")
                .count().as("count");
        
        Aggregation aggregation = Aggregation.newAggregation(matchUser, groupByStatus);
        
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

    private long countChatBotsCreatedAfter(Instant date, String userEmail) {
        Date dateObj = Date.from(date);
        Query query = new Query(Criteria.where("createdBy").is(userEmail)
                .and("createdAt").gte(dateObj));
        return mongoTemplate.count(query, ChatBot.class);
    }

    private Map<String, Long> getChatBotsByDataSource(String userEmail) {
        Query query = new Query(Criteria.where("createdBy").is(userEmail));
        List<ChatBot> userChatBots = mongoTemplate.find(query, ChatBot.class);
        
        Map<String, Long> dataSourceMap = new HashMap<>();
        for (ChatBot chatBot : userChatBots) {
            String dataSource = chatBot.getSelectedDataSource();
            if (dataSource != null && !dataSource.isEmpty()) {
                dataSourceMap.put(dataSource, dataSourceMap.getOrDefault(dataSource, 0L) + 1);
            } else {
                dataSourceMap.put("NONE", dataSourceMap.getOrDefault("NONE", 0L) + 1);
            }
        }
        
        return dataSourceMap;
    }

    private long getConversationsCountAfter(Instant date, List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0;
        
        Date dateObj = Date.from(date);
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds)
                .and("createdAt").gte(dateObj));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private Map<String, Long> getConversationsByMode(List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return new HashMap<>();
        
        MatchOperation matchChatbots = Aggregation.match(Criteria.where("chatbotId").in(chatbotIds));
        GroupOperation groupByMode = Aggregation.group("mode")
                .count().as("count");
        
        Aggregation aggregation = Aggregation.newAggregation(matchChatbots, groupByMode);
        
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

    private long getAnonymousConversationsCount(List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0;
        
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds)
                .and("isAnonymous").is(true));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private long getAuthenticatedConversationsCount(List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0;
        
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds)
                .and("isAnonymous").is(false));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private long getMessagesCountAfter(Instant date, List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0;
        
        Date dateObj = Date.from(date);
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds)
                .and("createdAt").gte(dateObj));
        return mongoTemplate.count(query, UserChatHistory.class);
    }

    private double getAverageMessagesPerDay(int days, List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0.0;
        
        Instant startDate = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(days, ChronoUnit.DAYS);
        Date dateObj = Date.from(startDate);
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds)
                .and("createdAt").gte(dateObj));
        long messages = mongoTemplate.count(query, UserChatHistory.class);
        return (double) messages / days;
    }

    private long getPeakMessagesInDay(List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0;
        
        // Group by date and count messages
        MatchOperation matchChatbots = Aggregation.match(Criteria.where("chatbotId").in(chatbotIds));
        ProjectionOperation projectDate = Aggregation.project()
                .andExpression("dateToString('%Y-%m-%d', createdAt)").as("date");
        
        GroupOperation groupByDate = Aggregation.group("date")
                .count().as("count");
        
        SortOperation sort = Aggregation.sort(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "count"));
        
        LimitOperation limit = Aggregation.limit(1);
        
        Aggregation aggregation = Aggregation.newAggregation(
                matchChatbots,
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

    private Map<String, Long> getMessagesByHour(List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return new HashMap<>();
        
        MatchOperation matchChatbots = Aggregation.match(Criteria.where("chatbotId").in(chatbotIds));
        ProjectionOperation projectHour = Aggregation.project()
                .andExpression("hour(createdAt)").as("hour");
        
        GroupOperation groupByHour = Aggregation.group("hour")
                .count().as("count");
        
        Aggregation aggregation = Aggregation.newAggregation(
                matchChatbots,
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

    private long getActiveUsersCountAfter(Instant date, List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0;
        
        Date dateObj = Date.from(date);
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds)
                .and("createdAt").gte(dateObj));
        query.fields().include("email");
        
        List<UserChatHistory> histories = mongoTemplate.find(query, UserChatHistory.class);
        return histories.stream()
                .map(UserChatHistory::getEmail)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }
    
    private long getUniqueUsersForChatBots(List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0;
        
        Query query = new Query(Criteria.where("chatbotId").in(chatbotIds));
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

    private long getMessageCountForUser(String email, List<String> chatbotIds) {
        if (chatbotIds.isEmpty()) return 0;
        
        Query query = new Query(Criteria.where("email").is(email)
                .and("chatbotId").in(chatbotIds));
        return mongoTemplate.count(query, UserChatHistory.class);
    }
}

