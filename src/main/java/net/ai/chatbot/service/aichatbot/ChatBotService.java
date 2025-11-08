package net.ai.chatbot.service.aichatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationRequest;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.utils.AuthUtils;
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

    private final RedisTemplate<String, String> redisTemplate;

    public ChatBotService(ChatBotDao chatBotDao, RedisTemplate<String, String> redisTemplate) {
        this.chatBotDao = chatBotDao;
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
                .customFallbackMessage(request.getCustomFallbackMessage())
                .fallbackMessage(request.getFallbackMessage())
                .greetingMessage(request.getGreetingMessage())
                .selectedDataSource(request.getSelectedDataSource())
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

        postSaveEventTrigger(saved);

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
     * Update chatbot configuration
     */
    @Transactional
    public void updateChatBot(String id, ChatBotCreationRequest request) {
        log.info("Updating chatbot: {}", id);

        ChatBot existing = chatBotDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found with ID: " + id));

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

        // Update fields
        ChatBot updated = ChatBot.builder()
                .id(existing.getId())
                .name(request.getName())
                .title(request.getTitle())
                .hideName(request.getHideName())
                .instructions(request.getInstructions())
                .restrictToDataSource(request.getRestrictToDataSource())
                .customFallbackMessage(request.getCustomFallbackMessage())
                .fallbackMessage(request.getFallbackMessage())
                .greetingMessage(request.getGreetingMessage())
                .selectedDataSource(request.getSelectedDataSource())
                .qaPairs(qaPairs)
                .fileIds(request.getFileIds())
                .addedWebsites(request.getAddedWebsites())
                .addedTexts(request.getAddedTexts())
                .createdBy(existing.getCreatedBy())
                .createdAt(existing.getCreatedAt())
                .updatedAt(new Date())
                .status(existing.getStatus())
                .build();

        chatBotDao.save(updated);

        log.info("Chatbot updated successfully: {}", id);
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
    private void postSaveEventTrigger(ChatBot chatBot) {
        ObjectRecord<String, String> record = StreamRecords
                .newRecord()
                .ofObject(chatBot.getId())
                .withStreamKey(CHAT_BOT_CREATE_EVENT_STREAM);

        RecordId recordId = redisTemplate.opsForStream().add(record);

        log.info("redis event created for chatbot: {}, redis eventId:{}, for user: {}", chatBot.getId(), recordId, AuthUtils.getEmail());
    }
}

