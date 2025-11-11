package net.ai.chatbot.controller.embeddedChat;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.service.embeddedChat.EmbeddedChatService;
import net.ai.chatbot.service.n8n.GenericN8NService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/website/")
public class WebsiteEmbeddingChatController {

    @Autowired
    private GenericN8NService<Message, Object> n8nService;

    @Autowired
    private EmbeddedChatService embeddedChatService;

    @Value("${n8n.webhook.knowledgebase.chat.url}")
    private String webhookKnowledgebaseChatUrl;

    /**
     * Send a single message to N8N workflow
     */
    @PostMapping("/{chatbotId}")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(@RequestBody Message message,
                                                               @PathVariable String chatbotId) {
        
        log.info("Received chat request for chatbotId: {}", chatbotId);

        message.setRole("user");
        N8NChatResponse<Object> response = n8nService.sendMessageWithSession(
                message,
                AuthUtils.getEmail(),
                webhookKnowledgebaseChatUrl
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{chatbotId}/{sessionId}")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(@RequestBody Message message,
                                                               @PathVariable String chatbotId,
                                                               @PathVariable String sessionId) {

        log.info("Received chat request for chatbotId: {}, sessionId: {}", chatbotId, sessionId);

        message.setRole("user");
        N8NChatResponse<Object> response = n8nService.sendMessageWithSession(
                message,
                sessionId,
                webhookKnowledgebaseChatUrl
        );

        return ResponseEntity.ok(response);
    }

}
