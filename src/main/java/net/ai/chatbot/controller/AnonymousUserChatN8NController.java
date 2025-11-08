package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.service.n8n.GenericN8NService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/n8n/anonymous")
public class AnonymousUserChatN8NController {

    @Autowired
    private GenericN8NService<Message, Object> n8nService;

    /**
     * Send a single message to N8N workflow
     */
    @PostMapping("/chat")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(
            @RequestBody Message message,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl) {
        
        log.info("Received chat request for workflow: {}", workflowId);
        
        N8NChatResponse<Object> response = n8nService.sendMessage(message, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Send multiple messages to N8N workflow
     */
    @PostMapping("/chat/batch")
    public ResponseEntity<N8NChatResponse<Object>> sendMessages(
            @RequestBody List<Message> messages,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl) {
        
        log.info("Received batch chat request for workflow: {}", workflowId);
        
        N8NChatResponse<Object> response = n8nService.sendMessages(messages, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Send message with session context
     */
    @PostMapping("/chat/session")
    public ResponseEntity<N8NChatResponse<Object>> sendMessageWithSession(
            @RequestBody Message message,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl,
            @RequestParam(required = false) String sessionId) {
        
        String currentSessionId = sessionId != null ? sessionId : AuthUtils.getEmail();
        log.info("Received session chat request for workflow: {} with session: {}", workflowId, currentSessionId);
        
        N8NChatResponse<Object> response = n8nService.sendMessageWithSession(message, currentSessionId, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Send custom input with full control over the request
     */
    @PostMapping("/chat/custom")
    public ResponseEntity<N8NChatResponse<Object>> sendCustomInput(
            @RequestBody N8NChatInput<Message> customInput) {
        
        log.info("Received custom chat request for workflow: {}", customInput.getWebhookUrl());
        
        N8NChatResponse<Object> response = n8nService.sendCustomInput(customInput);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for N8N workflows
     */
    @GetMapping("/health/{workflowId}")
    public ResponseEntity<N8NChatResponse<Object>> healthCheck(
            @PathVariable String workflowId,
            @RequestParam String webhookUrl) {
        
        log.info("Health check request for workflow: {}", workflowId);
        
        Message healthMessage = Message.builder()
                .role("system")
                .content("health_check")
                .build();
        
        N8NChatResponse<Object> response = n8nService.sendMessage(healthMessage, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get workflow status
     */
    @GetMapping("/status/{workflowId}")
    public ResponseEntity<N8NChatResponse<Object>> getWorkflowStatus(
            @PathVariable String workflowId,
            @RequestParam String webhookUrl) {
        
        log.info("Status check request for workflow: {}", workflowId);
        
        Message statusMessage = Message.builder()
                .role("system")
                .content("status_check")
                .build();
        
        N8NChatResponse<Object> response = n8nService.sendMessage(statusMessage, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }
}
