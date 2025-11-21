package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import net.ai.chatbot.service.n8n.GenericN8NService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/n8n/authenticated")
public class AuthenticatedUserChatN8NController {

    @Autowired
    private GenericN8NService<Message, Object> n8nService;

    @Autowired
    private ChatBotService chatBotService;

    //    @Value("${n8n.webhook.knowledgebase.chat.url}")
    private String webhookUrl = "http://localhost:5678/webhook/beab6fcf-f27a-4d26-8923-5f95e8190fea";

    /**
     * Get session ID from authentication context or return default
     */
    private String getSessionId() {
        try {
            return AuthUtils.getEmail();
        } catch (Exception e) {
            log.warn("Could not get email from authentication context, using default session ID");
            return "default_session_" + System.currentTimeMillis();
        }
    }

    @PostMapping("/chatHistory/{chatbotId}")
    public ResponseEntity<List<UserChatHistory>> getConversationList(@PathVariable String chatbotId) {

        List<UserChatHistory> chatHistories = chatBotService.getChatConversationList(chatbotId);

        return ResponseEntity.ok(chatHistories);
    }

    @PostMapping("/chatHistory/{chatbotId}/{conversationId}")
    public ResponseEntity<List<UserChatHistory>> getChatHistory(@PathVariable String chatbotId, @PathVariable String conversationId) {

        List<UserChatHistory> chatHistories = chatBotService.getChatHistory(chatbotId, conversationId);

        return ResponseEntity.ok(chatHistories);
    }

    /**
     * Send a single message to N8N workflow
     */
    @PostMapping("/chat")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(@RequestBody Message message) {

        ChatBot chatBot = chatBotService.getChatBot(message.getChatbotId());

        N8NChatResponse<Object> response = n8nService.sendMessage(chatBot, message, webhookUrl);

        return ResponseEntity.ok(response);
    }

}
