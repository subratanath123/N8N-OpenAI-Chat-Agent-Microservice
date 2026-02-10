package net.ai.chatbot.service.googlecalendar;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.entity.ChatBot;
import org.springframework.stereotype.Service;

/**
 * Service to verify chatbot ownership
 */
@Service
@Slf4j
public class ChatbotOwnershipService {

    private final ChatBotDao chatBotDao;

    public ChatbotOwnershipService(ChatBotDao chatBotDao) {
        this.chatBotDao = chatBotDao;
    }

    /**
     * Verify that the given user owns the chatbot
     *
     * @param chatbotId The chatbot ID
     * @param userEmail The user's email (from Clerk JWT)
     * @throws IllegalArgumentException if chatbot not found
     * @throws SecurityException if user does not own the chatbot
     */
    public void verifyOwnership(String chatbotId, String userEmail) {
        log.debug("Verifying chatbot ownership: chatbotId={}, userEmail={}", chatbotId, userEmail);

        ChatBot chatBot = chatBotDao.findById(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found: " + chatbotId));

        if (!chatBot.getCreatedBy().equals(userEmail)) {
            log.warn("Ownership verification failed: chatbotId={}, userEmail={}, owner={}",
                    chatbotId, userEmail, chatBot.getCreatedBy());
            throw new SecurityException("User does not own this chatbot");
        }

        log.debug("Ownership verification successful: chatbotId={}, userEmail={}", chatbotId, userEmail);
    }

    /**
     * Check if user owns the chatbot (without throwing exception)
     *
     * @param chatbotId The chatbot ID
     * @param userEmail The user's email
     * @return true if user owns the chatbot, false otherwise
     */
    public boolean ownschatbot(String chatbotId, String userEmail) {
        try {
            verifyOwnership(chatbotId, userEmail);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}







