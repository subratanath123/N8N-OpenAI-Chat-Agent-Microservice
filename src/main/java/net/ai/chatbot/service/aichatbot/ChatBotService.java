package net.ai.chatbot.service.aichatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationRequest;
import net.ai.chatbot.entity.*;
import net.ai.chatbot.entity.ChatBot.QAPair;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static net.ai.chatbot.constants.Constants.CHAT_BOT_CREATE_EVENT_STREAM;

@Service
@Slf4j
public class ChatBotService {

    private final ChatBotDao chatBotDao;
    private final MongoTemplate mongoTemplate;

    private final RedisTemplate<String, String> redisTemplate;

    public ChatBotService(ChatBotDao chatBotDao, MongoTemplate mongoTemplate,
                          RedisTemplate<String, String> redisTemplate) {
        this.chatBotDao = chatBotDao;
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Create a new chatbot with the given configuration
     */
    @Transactional
    public String createChatBot(ChatBotCreationRequest request, String createdBy) {
        log.info("Creating chatbot: Name={}, Title={}", request.getName(), request.getTitle());

        // Check if chatbot with same name already exists for this user
        if (chatBotDao.existsByNameAndCreatedBy(request.getName(), createdBy)) {
            throw new IllegalArgumentException("Chatbot with name '" + request.getName() + "' already exists");
        }

        // Convert Q&A pairs from DTO to Entity
        List<QAPair> qaPairs = null;
        if (request.getQaPairs() != null) {
            qaPairs = request.getQaPairs().stream()
                    .map(qa -> QAPair.builder()
                            .question(qa.getQuestion())
                            .answer(qa.getAnswer())
                            .build())
                    .toList();
        }

        // Save chatbot to MongoDB
        ChatBot chatbot = ChatBot.builder()
                .email(AuthUtils.getEmail())
                .name(request.getName())
                .title(request.getTitle())
                .hideName(request.getHideName())
                .instructions(request.getInstructions())
                .restrictToDataSource(request.getRestrictToDataSource())
                .fallbackMessage(request.getFallbackMessage())
                .greetingMessage(request.getGreetingMessage())
                .selectedDataSource(request.getSelectedDataSource())
                .width(request.getWidth())
                .height(request.getHeight())
                .qaPairs(qaPairs)
                .fileIds(request.getFileIds())
                .addedWebsites(request.getAddedWebsites())
                .addedTexts(request.getAddedTexts())
                .createdBy(createdBy)
                .createdAt(new Date())
                .updatedAt(new Date())
                .status("CREATED")
                .build();

        ChatBot saved = chatBotDao.save(chatbot);

        ChatBotTask chatBotTask = ChatBotTask.builder()
                .chatbotId(chatbot.getId())
                .fileIds(chatbot.getFileIds())
                .qaPairs(chatbot.getQaPairs())
                .addedTexts(chatbot.getAddedTexts())
                .addedWebsites(chatbot.getAddedWebsites())
                .build();

        chatBotTask = mongoTemplate.save(chatBotTask);

        postSaveEventTrigger(chatBotTask);

        log.info("Chatbot created successfully with ID: {}", saved.getId());

        return saved.getId();
    }

    /**
     * Update chatbot configuration
     */
    @Transactional
    public ChatBot updateChatBot(String id, ChatBotCreationRequest request) {
        log.info("Updating chatbot: {}", id);

        ChatBot existing = chatBotDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found with ID: " + id));

        // Merge lists without duplicates
        List<String> mergedFileIds = mergeList(existing.getFileIds(), request.getFileIds());
        List<QAPair> mergedQaPairs = mergeList(existing.getQaPairs(), request.getQaPairs());
        List<String> mergedTexts = mergeList(existing.getAddedTexts(), request.getAddedTexts());
        List<String> mergedWebsites = mergeList(existing.getAddedWebsites(), request.getAddedWebsites());

        // Build updated ChatBot
        ChatBot updated = ChatBot.builder()
                .id(existing.getId())
                .email(existing.getEmail())
                .updatedAt(new Date())
                .status(existing.getStatus())
                .createdBy(existing.getCreatedBy())
                .createdAt(existing.getCreatedAt())
                .name(request.getName())
                .title(request.getTitle())
                .hideName(request.getHideName())
                .instructions(request.getInstructions())
                .restrictToDataSource(request.getRestrictToDataSource())
                .fallbackMessage(request.getFallbackMessage())
                .greetingMessage(request.getGreetingMessage())
                .selectedDataSource(request.getSelectedDataSource())
                .width(request.getWidth())
                .height(request.getHeight())

                // merged new + existing
                .fileIds(mergedFileIds)
                .qaPairs(mergedQaPairs)
                .addedTexts(mergedTexts)
                .addedWebsites(mergedWebsites)

                .build();

        updated = chatBotDao.save(updated);

        // Build ChatBotTask with merged data
        ChatBotTask chatBotTask = ChatBotTask.builder()
                .chatbotId(updated.getId())
                .fileIds(mergedFileIds)
                .qaPairs(mergedQaPairs)
                .addedTexts(mergedTexts)
                .addedWebsites(mergedWebsites)
                .build();

        chatBotTask = mongoTemplate.save(chatBotTask);

        postSaveEventTrigger(chatBotTask);

        log.info("Chatbot updated successfully: {}", id);

        return updated;
    }

    /**
     * Retrieve chatbot by ID
     */
    public ChatBot getChatBot(String id) {
        log.info("Retrieving chatbot: {}", id);
        return chatBotDao.findById(id).orElse(null);
    }

    /**
     * Retrieve getKnowledgeBaseList
     */
    public List<KnowledgeBase> getKnowledgeBaseList(String id) {
        log.info("Retrieving getKnowledgeBaseList: {}", id);

        return mongoTemplate.find(
                new Query().addCriteria(Criteria.where("chatbotId").is(id)),
                KnowledgeBase.class
        );
    }

    /**
     * Retrieve getChatBotFrom MessengerId
     */
    public ChatBot getChabotFromMessengerId(String messengerId) {
        log.info("Retrieving getChabot for messengerId: {}", messengerId);

        MessengerIntegration messengerIntegration = getMessengerIntegration(messengerId);

        if (messengerIntegration == null) {
            return null;
        }

        ChatBot chatBot = getChatBot(messengerIntegration.chatbotId());
        chatBot.setMessengerIntegration(messengerIntegration);

        return chatBot;
    }

    /**
     * Retrieve getChatBotFrom MessengerId
     */
    public ChatBot getChabotFromPhoneNumberID(String phonenumberId) {
        log.info("Retrieving getChabot for whatsappId: {}", phonenumberId);

        WhatsAppIntegration whatsAppIntegration = getWhatsappIntegration(phonenumberId);

        if (whatsAppIntegration == null) {
            return null;
        }

        ChatBot chatBot = getChatBot(whatsAppIntegration.chatbotId());
        chatBot.setWhatsAppIntegration(whatsAppIntegration);

        return chatBot;
    }

    public MessengerIntegration getMessengerIntegration(String messengerId) {
        MessengerIntegration messengerIntegration = mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("pageId").is(messengerId)),
                MessengerIntegration.class
        );
        return messengerIntegration;
    }

    public WhatsAppIntegration getWhatsappIntegration(String phoneNumberId) {
        WhatsAppIntegration whatsAppIntegration = mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("phoneNumberId").is(phoneNumberId)),
                WhatsAppIntegration.class
        );
        return whatsAppIntegration;
    }

    /**
     * Utility: merges without duplicates while supporting nulls
     */
    private <T> List<T> mergeList(List<T> existing, List<T> incoming) {
        Set<T> set = new LinkedHashSet<>();
        if (existing != null) set.addAll(existing);
        if (incoming != null) set.addAll(incoming);
        return new ArrayList<>(set);
    }

    /**
     * Delete chatbot
     */
    @Transactional
    public void deleteChatBot(String id) {
        log.info("Deleting chatbot: {}", id);

        if (!chatBotDao.existsById(id)) {
            throw new IllegalArgumentException("Chatbot not found with ID: " + id);
        }

        chatBotDao.deleteById(id);

        log.info("Chatbot deleted successfully: {}", id);
    }

    /*
     * Return first chat of every conversation for the given chatbot
     */
    public List<UserChatHistory> getChatConversationList(String chatbotId) {
        log.info("Getting All conversations: {}", chatbotId);

        MatchOperation match = Aggregation.match(
                Criteria.where("chatbotId").is(chatbotId)
        );

        // sort all records inside each conversation by createdAt
        SortOperation sort = Aggregation.sort(Sort.by(Sort.Direction.ASC, "createdAt"));

        // group by conversationid and extract first fields
        GroupOperation group = Aggregation.group("conversationid")
                .first("conversationid").as("conversationid")
                .first("userMessage").as("userMessage")
                .first("aiMessage").as("aiMessage")
                .first("createdAt").as("createdAt")
                .first("email").as("email")
                .first("mode").as("mode")
                .first("isAnonymous").as("isAnonymous");

        // select only required fields
        ProjectionOperation project = Aggregation.project("conversationid", "userMessage", "aiMessage", "createdAt", "email", "mode", "isAnonymous")
                .andExclude("_id");

        Aggregation aggregation = Aggregation.newAggregation(match, sort, group, project);

        return mongoTemplate.aggregate(
                aggregation,
                "n8n_chat_session_histories",
                UserChatHistory.class
        ).getMappedResults();
    }

    @Transactional
    public List<UserChatHistory> getChatHistory(String chatbotId, String conversationId) {
        log.info("Getting All chats of the conversation: {}", conversationId);

        Criteria criteria = Criteria.where("chatbotId").is(chatbotId)
                .and("conversationid").is(conversationId);

        // Only filter by email if user is authenticated and not an admin
        String userEmail = AuthUtils.getEmail();
        if (userEmail != null && !AuthUtils.isAdmin()) {
            criteria = criteria.and("email").is(userEmail);
            log.debug("Filtering chat history by email: {}", userEmail);
        } else if (userEmail == null) {
            log.debug("Public/unauthenticated request - returning all messages in conversation");
        } else {
            log.debug("Admin request - returning all messages in conversation");
        }

        return mongoTemplate.find(new Query().addCriteria(criteria), UserChatHistory.class);
    }

    /**
     * Save admin reply to conversation
     * Used when admin/manager sends reply on behalf of chatbot
     */
    @Transactional
    public UserChatHistory saveAdminReply(String conversationId, String chatbotId, 
                                          String message, String adminEmail) {
        log.info("Saving admin reply to conversation: {}, chatbot: {}, admin: {}", 
                conversationId, chatbotId, adminEmail);
        
        // Get the conversation user's email from existing messages
        // This ensures the admin reply appears in the user's conversation history
        String conversationUserEmail = getConversationUserEmail(conversationId, chatbotId);
        
        if (conversationUserEmail == null) {
            log.warn("Could not find user email for conversation: {}, using admin email as fallback", conversationId);
            conversationUserEmail = adminEmail;
        }
        
        // Generate unique message ID
        String messageId = "msg_" + System.currentTimeMillis() + "_" + 
                          UUID.randomUUID().toString().substring(0, 8);
        
        // Create UserChatHistory record for admin reply
        UserChatHistory adminReply = UserChatHistory.builder()
                .id(messageId)
                .conversationid(conversationId)
                .chatbotId(chatbotId)
                .email(conversationUserEmail) // Use conversation user's email, not admin's
                .aiMessage(message)
                .userMessage(null) // No user message for admin replies
                .role("assistant")
                .senderType("admin_reply")
                .adminUserId(adminEmail) // Track who sent the reply
                .status("sent")
                .createdAt(java.time.Instant.now())
                .mode("admin")
                .isAnonymous(false)
                .build();
        
        // Save to MongoDB
        UserChatHistory saved = mongoTemplate.save(adminReply);
        
        log.info("Admin reply saved successfully: messageId={}, conversationUserEmail={}", 
                messageId, conversationUserEmail);
        return saved;
    }
    
    /**
     * Get the user's email from an existing conversation
     * Finds the first message in the conversation and returns the email field
     */
    private String getConversationUserEmail(String conversationId, String chatbotId) {
        try {
            Criteria criteria = Criteria.where("conversationid").is(conversationId)
                    .and("chatbotId").is(chatbotId);
            
            Query query = new Query(criteria);
            query.limit(1);
            query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
            
            UserChatHistory firstMessage = mongoTemplate.findOne(query, UserChatHistory.class);
            
            if (firstMessage != null && firstMessage.getEmail() != null) {
                log.debug("Found conversation user email: {} for conversation: {}", 
                         firstMessage.getEmail(), conversationId);
                return firstMessage.getEmail();
            }
            
            log.warn("No messages found for conversation: {}, chatbot: {}", conversationId, chatbotId);
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching conversation user email: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Verify that conversation exists and belongs to the specified chatbot
     */
    public boolean verifyConversationOwnership(String conversationId, String chatbotId) {
        log.debug("Verifying conversation ownership: conversationId={}, chatbotId={}", 
                 conversationId, chatbotId);
        
        Criteria criteria = Criteria.where("conversationid").is(conversationId)
                .and("chatbotId").is(chatbotId);
        
        Query query = new Query(criteria);
        query.limit(1);
        
        boolean exists = mongoTemplate.exists(query, UserChatHistory.class);
        
        if (!exists) {
            log.warn("Conversation not found or does not belong to chatbot: conversationId={}, chatbotId={}", 
                    conversationId, chatbotId);
        }
        
        return exists;
    }

    /**
     * Get all chatbots by user
     */
    public List<ChatBot> getChatBotsByUser(String createdBy) {
        log.info("Retrieving chatbots for user: {}", createdBy);
        return chatBotDao.findByCreatedBy(createdBy);
    }

    /**
     * Event Trigger for post processing of chatbot creation
     */
    private void postSaveEventTrigger(ChatBotTask chatBotTask) {
        ObjectRecord<String, String> record = StreamRecords
                .newRecord()
                .ofObject(chatBotTask.getId())
                .withStreamKey(CHAT_BOT_CREATE_EVENT_STREAM);

        RecordId recordId = redisTemplate.opsForStream().add(record);

        log.info("redis event created for chatBotTask:{} chatbot: {}, redis eventId:{}, for user: {}", chatBotTask.getId(), chatBotTask.getChatbotId(), recordId, AuthUtils.getEmail());
    }
}

