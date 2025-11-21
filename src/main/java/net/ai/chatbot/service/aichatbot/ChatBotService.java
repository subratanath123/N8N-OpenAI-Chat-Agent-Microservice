package net.ai.chatbot.service.aichatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationRequest;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.ChatBotTask;
import net.ai.chatbot.entity.KnowledgeBase;
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

import java.util.Date;
import java.util.List;

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
        List<ChatBot.QAPair> qaPairs = null;
        if (request.getQaPairs() != null) {
            qaPairs = request.getQaPairs().stream()
                    .map(qa -> ChatBot.QAPair.builder()
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

        postSaveEventTrigger(chatBotTask);

        log.info("Chatbot created successfully with ID: {}", saved.getId());

        return saved.getId();
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
     * Update chatbot configuration
     */
    @Transactional
    public ChatBot updateChatBot(String id, ChatBotCreationRequest request) {
        log.info("Updating chatbot: {}", id);

        ChatBot existing = chatBotDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found with ID: " + id));

        existing.getFileIds().addAll(request.getFileIds());
        existing.getQaPairs().addAll(request.getQaPairs());
        existing.getAddedTexts().addAll(request.getAddedTexts());
        existing.getAddedWebsites().addAll(request.getAddedWebsites());

        // Update fields
        ChatBot updated = ChatBot.builder()
                .id(existing.getId())
                .email(existing.getEmail())
                .updatedAt(new Date())
                .status(existing.getStatus())
                .fileIds(existing.getFileIds())
                .qaPairs(existing.getQaPairs())
                .addedTexts(existing.getAddedTexts())
                .addedWebsites(existing.getAddedWebsites())
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
                .build();

        updated = chatBotDao.save(updated);

        ChatBotTask chatBotTask = ChatBotTask.builder()
                .chatbotId(updated.getId())
                .fileIds(request.getFileIds())
                .qaPairs(request.getQaPairs())
                .addedTexts(request.getAddedTexts())
                .addedWebsites(request.getAddedWebsites())
                .build();

        postSaveEventTrigger(chatBotTask);

        log.info("Chatbot updated successfully: {}", id);

        return updated;
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

        if (!AuthUtils.isAdmin()) {
            criteria = criteria.and("email").is(AuthUtils.getEmail());
        }

        return mongoTemplate.find(new Query().addCriteria(criteria), UserChatHistory.class);
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

