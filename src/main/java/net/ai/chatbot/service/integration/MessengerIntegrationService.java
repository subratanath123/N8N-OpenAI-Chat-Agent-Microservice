package net.ai.chatbot.service.integration;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.MessengerIntegration;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing Messenger integrations
 */
@Service
@Slf4j
public class MessengerIntegrationService {

    private final MongoTemplate mongoTemplate;
    private final ChatBotDao chatBotDao;

    public MessengerIntegrationService(MongoTemplate mongoTemplate, ChatBotDao chatBotDao) {
        this.mongoTemplate = mongoTemplate;
        this.chatBotDao = chatBotDao;
    }

    /**
     * Create or update a messenger integration for a chatbot
     *
     * @param messengerIntegration The integration data
     * @param userEmail            The authenticated user's email
     * @return The saved MessengerIntegration entity
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException    if chatbot not found or permission denied
     */
    @Transactional
    public MessengerIntegration createOrUpdateMessengerIntegration(MessengerIntegration messengerIntegration, String userEmail) {
        log.info("Creating/updating Messenger setup for chatbot: {} by user: {}", messengerIntegration.chatbotId(), userEmail);

        // Validate chatbotId is provided
        validateChatbotId(messengerIntegration.chatbotId());

        // Validate chatbot exists and user has permission
        validateAndGetChatBot(messengerIntegration.chatbotId(), userEmail);

        // Validate required fields
        validateRequiredFields(messengerIntegration);

        // Check if messenger integration already exists for this chatbot
        Query existingQuery = new Query(Criteria.where("chatbotId").is(messengerIntegration.chatbotId()));
        MessengerIntegration existingIntegration = mongoTemplate.findOne(existingQuery, MessengerIntegration.class);

        MessengerIntegration savedIntegration;
        if (existingIntegration != null) {
            // Update existing integration
            log.info("Updating existing messenger integration for chatbot: {}", messengerIntegration.chatbotId());
            // Preserve existing enabled status if not provided, otherwise use the provided value
            Boolean enabledStatus = messengerIntegration.enabled() != null 
                    ? messengerIntegration.enabled() 
                    : (existingIntegration.enabled() != null ? existingIntegration.enabled() : true);
            
            MessengerIntegration updatedIntegration = MessengerIntegration.builder()
                    .id(existingIntegration.id())
                    .chatbotId(messengerIntegration.chatbotId())
                    .pageName(messengerIntegration.pageName())
                    .pageId(messengerIntegration.pageId())
                    .accessToken(messengerIntegration.accessToken())
                    .verifyToken(messengerIntegration.verifyToken())
                    .enabled(enabledStatus)
                    .build();

            savedIntegration = mongoTemplate.save(updatedIntegration);

        } else {
            // Create new integration - default to enabled (true)
            log.info("Creating new messenger integration for chatbot: {}", messengerIntegration.chatbotId());
            Boolean enabledStatus = messengerIntegration.enabled() != null ? messengerIntegration.enabled() : true;
            MessengerIntegration newIntegration = MessengerIntegration.builder()
                    .chatbotId(messengerIntegration.chatbotId())
                    .pageName(messengerIntegration.pageName())
                    .pageId(messengerIntegration.pageId())
                    .accessToken(messengerIntegration.accessToken())
                    .verifyToken(messengerIntegration.verifyToken())
                    .enabled(enabledStatus)
                    .build();
            savedIntegration = mongoTemplate.save(newIntegration);
        }

        log.info("Messenger setup completed successfully for chatbot: {} with integration ID: {}",
                messengerIntegration.chatbotId(), savedIntegration.id());

        return savedIntegration;
    }

    /**
     * Get messenger integration by chatbot ID
     *
     * @param chatbotId The chatbot ID
     * @param userEmail The authenticated user's email
     * @return Optional MessengerIntegration
     */
    public Optional<MessengerIntegration> getMessengerIntegrationByChatbotId(String chatbotId, String userEmail) {
        // Validate user has access to the chatbot
        validateAndGetChatBot(chatbotId, userEmail);

        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        MessengerIntegration integration = mongoTemplate.findOne(query, MessengerIntegration.class);
        return Optional.ofNullable(integration);
    }

    /**
     * Toggle the enabled status of a messenger integration
     *
     * @param chatbotId The chatbot ID
     * @param enabled    The new enabled status
     * @param userEmail  The authenticated user's email
     * @return The updated MessengerIntegration entity
     * @throws IllegalStateException if integration not found
     */
    @Transactional
    public MessengerIntegration toggleMessengerIntegrationStatus(String chatbotId, Boolean enabled, String userEmail) {
        log.info("Toggling messenger integration status for chatbot: {} to {} by user: {}", chatbotId, enabled, userEmail);

        // Validate chatbotId is provided
        validateChatbotId(chatbotId);

        // Validate chatbot exists and user has permission
        validateAndGetChatBot(chatbotId, userEmail);

        // Get existing integration
        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        MessengerIntegration existingIntegration = mongoTemplate.findOne(query, MessengerIntegration.class);

        if (existingIntegration == null) {
            throw new IllegalStateException("Messenger integration not found for chatbot: " + chatbotId);
        }

        // Update enabled status
        MessengerIntegration updatedIntegration = MessengerIntegration.builder()
                .id(existingIntegration.id())
                .chatbotId(existingIntegration.chatbotId())
                .pageName(existingIntegration.pageName())
                .pageId(existingIntegration.pageId())
                .accessToken(existingIntegration.accessToken())
                .verifyToken(existingIntegration.verifyToken())
                .enabled(enabled)
                .build();

        MessengerIntegration savedIntegration = mongoTemplate.save(updatedIntegration);

        log.info("Messenger integration status updated for chatbot: {} to {} by user: {}", 
                chatbotId, enabled, userEmail);

        return savedIntegration;
    }

    private void validateChatbotId(String chatbotId) {
        if (chatbotId == null || chatbotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Chatbot ID is required");
        }
    }

    private ChatBot validateAndGetChatBot(String chatbotId, String userEmail) {
        Optional<ChatBot> chatbotOpt = chatBotDao.findById(chatbotId);
        if (chatbotOpt.isEmpty()) {
            throw new IllegalStateException("Chatbot not found with ID: " + chatbotId);
        }

        ChatBot chatBot = chatbotOpt.get();
        if (!chatBot.getCreatedBy().equals(userEmail) && !AuthUtils.isAdmin()) {
            throw new IllegalStateException("You don't have permission to setup messenger for this chatbot");
        }

        return chatBot;
    }

    private void validateRequiredFields(MessengerIntegration messengerIntegration) {
        if (messengerIntegration.pageId() == null || messengerIntegration.pageId().trim().isEmpty()) {
            throw new IllegalArgumentException("Page ID is required");
        }

        if (messengerIntegration.accessToken() == null || messengerIntegration.accessToken().trim().isEmpty()) {
            throw new IllegalArgumentException("Access Token is required");
        }
    }
}

