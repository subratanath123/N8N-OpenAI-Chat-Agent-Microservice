package net.ai.chatbot.service.mongodb;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.UserChatHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserChatHistoryService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Get chat history list for a specific user - returns earliest message from each unique conversation
     */
    public List<UserChatHistory> getUserChatHistory(String userEmail, int limit, int offset) {
        try {
            // Use MongoDB aggregation to get the earliest message from each unique conversationId
            // This is equivalent to GROUP BY conversationId and MIN(lastMessage.createdAt)

            Query query = new Query();
            query.addCriteria(Criteria.where("email").is(userEmail));
            query.addCriteria(Criteria.where("conversationid").ne(null)); // Only conversations with IDs

            // Sort by creation time to get earliest messages first
            query.with(Sort.by(Sort.Direction.ASC, "lastMessage.createdAt"));

            List<UserChatHistory> allMessages = mongoTemplate.find(query, UserChatHistory.class);

            // Group by conversationId and keep only the earliest message from each conversation
            Map<String, UserChatHistory> earliestByConversation = new LinkedHashMap<>();

            for (UserChatHistory message : allMessages) {
                String conversationId = message.getConversationid();
                if (conversationId != null && !earliestByConversation.containsKey(conversationId)) {
                    earliestByConversation.put(conversationId, message);
                }
            }

            // Convert to list and sort by creation time (newest conversations first)
            List<UserChatHistory> result = earliestByConversation.values().stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .collect(Collectors.toList());

            // Apply pagination
            int fromIndex = offset;
            int toIndex = Math.min(offset + limit, result.size());

            if (fromIndex >= result.size()) {
                return Collections.emptyList();
            }

            List<UserChatHistory> paginatedResult = result.subList(fromIndex, toIndex);

            log.info("Retrieved {} unique conversations (earliest messages) for user: {}", paginatedResult.size(), userEmail);

            return paginatedResult;

        } catch (Exception e) {
            log.error("Error retrieving chat history for user {}: {}", userEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve chat history", e);
        }
    }


    /**
     * Get all messages from a specific conversation for a user
     */
    public List<UserChatHistory> getAllMessagesFromConversation(String conversationId, String userEmail) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("conversationid").is(conversationId))
                    .addCriteria(Criteria.where("email").is(userEmail));
            query.with(Sort.by(Sort.Direction.ASC, "lastMessage.createdAt"));

            List<UserChatHistory> messages = mongoTemplate.find(query, UserChatHistory.class);

            log.info("Retrieved {} messages from conversation: {} for user: {}",
                    messages.size(), conversationId, userEmail);

            return messages;

        } catch (Exception e) {
            log.error("Error retrieving all messages from conversation {} for user {}: {}",
                    conversationId, userEmail, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Count unique conversations for a user
     */
    public long countUserChatHistories(String userEmail) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("email").is(userEmail));
            query.addCriteria(Criteria.where("conversationid").ne(null));

            List<UserChatHistory> allMessages = mongoTemplate.find(query, UserChatHistory.class);

            // Count unique conversation IDs
            Set<String> uniqueConversations = allMessages.stream()
                    .map(UserChatHistory::getConversationid)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            return uniqueConversations.size();

        } catch (Exception e) {
            log.error("Error counting chat histories for user {}: {}", userEmail, e.getMessage(), e);
            return 0;
        }
    }
}
