package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.service.n8n.GenericN8NService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/n8n/anonymous")
public class AnonymousUserChatN8NController {

    @Autowired
    private GenericN8NService<Message, Object> n8nService;

    @Value("${n8n.webhook.knowledgebase.chat.url}")
    private String webhookUrl;
    
    /**
     * Send a single message to N8N workflow
     */
    @PostMapping("/chat")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(@RequestBody Message message) {

        N8NChatResponse<Object> response = n8nService.sendMessage(message, webhookUrl);

        return ResponseEntity.ok(response);
    }

    /**
     * Send message with session context
     */
    @PostMapping("/chat/session")
    public ResponseEntity<N8NChatResponse<Object>> sendMessageWithSession(
            @RequestBody Message message,
            @RequestParam(required = false) String sessionId) {

        String currentSessionId = sessionId != null ? sessionId : AuthUtils.getEmail();
        log.info("Received session chat request for workflow: {} with session: {}", currentSessionId);

        N8NChatResponse<Object> response = n8nService.sendMessageWithSession(message, currentSessionId, webhookUrl);

        return ResponseEntity.ok(response);
    }
}
