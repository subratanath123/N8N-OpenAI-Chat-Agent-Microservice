package net.ai.chatbot.controller.aichatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/public")
public class AIChatBotPublicEndpointController {

    private final ChatBotService chatBotService;

    public AIChatBotPublicEndpointController(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    /**
     * Get chatbot by ID
     * GET /v1/api/chatbot/{id}
     */
    @GetMapping("/chatbot/{id}")
    public ResponseEntity<ChatBot> getChatBot(@PathVariable String id) {
        log.info("Getting chatbot: {}", id);

        var chatbot = chatBotService.getChatBot(id);

        if (chatbot == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(chatbot);
    }

    @GetMapping("/chatHistory/{chatbotId}/{conversationId}")
    public ResponseEntity<List<UserChatHistory>> getChatHistory(@PathVariable String chatbotId, @PathVariable String conversationId) {

        List<UserChatHistory> chatHistories = chatBotService.getChatHistory(chatbotId, conversationId);

        return ResponseEntity.ok(chatHistories);
    }

    @GetMapping("/chatbot/messenger/{messengerId}")
    public ResponseEntity<ChatBot> getMessengerSetup(@PathVariable String messengerId) {
        try {

            log.info("Getting chatbot for messengerId: {}", messengerId);

            return ResponseEntity.ok(chatBotService.getChabotFromMessengerId(messengerId));

        } catch (Exception e) {
            log.error("Error getting messenger setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/chatbot/whatsapp/{phonenumberId}")
    public ResponseEntity<ChatBot> getWhatsappSetup(@PathVariable String phonenumberId) {
        try {

            log.info("Getting chatbot for phonenumberId: {}", phonenumberId);

            return ResponseEntity.ok(chatBotService.getChabotFromPhoneNumberID(phonenumberId));

        } catch (Exception e) {
            log.error("Error getting messenger setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}

