package net.ai.chatbot.service.chatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationRequest;
import net.ai.chatbot.entity.ChatBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ChatBotService {
    
    @Autowired
    private ChatBotDao chatBotDao;
    
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
        
        // Process different data sources
        if (request.getAddedWebsites() != null && !request.getAddedWebsites().isEmpty()) {
            processWebsites(request.getAddedWebsites());
        }
        
        if (request.getAddedTexts() != null && !request.getAddedTexts().isEmpty()) {
            processTexts(request.getAddedTexts());
        }
        
        if (request.getUploadedFiles() != null && !request.getUploadedFiles().isEmpty()) {
            processFiles(request.getUploadedFiles());
        }
        
        if (request.getQaPairs() != null && !request.getQaPairs().isEmpty()) {
            processQAPairs(request.getQaPairs());
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
            .uploadedFiles(request.getUploadedFiles())
            .addedWebsites(request.getAddedWebsites())
            .addedTexts(request.getAddedTexts())
            .createdBy(createdBy)
            .createdAt(new Date())
            .updatedAt(new Date())
            .status("CREATED")
            .build();
        
        ChatBot saved = chatBotDao.save(chatbot);
        
        log.info("Chatbot created successfully with ID: {}", saved.getId());
        
        return saved.getId();
    }
    
    /**
     * Process website URLs for training
     */
    private void processWebsites(java.util.List<String> websites) {
        log.info("Processing {} websites for training", websites.size());
        // TODO: Implement website scraping and training
    }
    
    /**
     * Process text content for training
     */
    private void processTexts(java.util.List<String> texts) {
        log.info("Processing {} text items for training", texts.size());
        // TODO: Implement text processing and training
    }
    
    /**
     * Process uploaded files for training
     */
    private void processFiles(java.util.List<String> files) {
        log.info("Processing {} files for training", files.size());
        // TODO: Implement file processing and training
    }
    
    /**
     * Process Q&A pairs for training
     */
    private void processQAPairs(java.util.List<ChatBotCreationRequest.QAPair> qaPairs) {
        log.info("Processing {} Q&A pairs for training", qaPairs.size());
        // TODO: Implement Q&A processing and training
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
            .uploadedFiles(request.getUploadedFiles())
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
}

