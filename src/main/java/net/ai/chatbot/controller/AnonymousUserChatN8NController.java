package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import net.ai.chatbot.service.n8n.GenericN8NService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/n8n/anonymous")
public class AnonymousUserChatN8NController {

    @Autowired
    private GenericN8NService<Message, Object> n8nService;

    @Autowired
    private ChatBotService chatBotService;

    @Value("${n8n.webhook.knowledgebase.chat.url}")
    private String webhookUrl;

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
