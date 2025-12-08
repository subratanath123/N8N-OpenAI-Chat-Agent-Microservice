package net.ai.chatbot.controller.aichatbot;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationResponse;
import net.ai.chatbot.entity.MessengerIntegration;
import net.ai.chatbot.service.integration.MessengerIntegrationService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/chatbot")
public class ExternalIntegrationController {

    private final MessengerIntegrationService messengerIntegrationService;

    public ExternalIntegrationController(MessengerIntegrationService messengerIntegrationService) {
        this.messengerIntegrationService = messengerIntegrationService;
    }

    /**
     * Create a new Messenger Setup configuration
     * POST /v1/api/chatbot/messenger/setup
     */
    @PostMapping("/messenger/setup")
    public ResponseEntity<ChatBotCreationResponse> createMessengerSetup(@Valid @RequestBody MessengerIntegration messengerIntegration) {
        try {
            String userEmail = AuthUtils.getEmail();

            MessengerIntegration savedIntegration = messengerIntegrationService
                    .createOrUpdateMessengerIntegration(messengerIntegration, userEmail);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatBotCreationResponse.builder()
                            .id(savedIntegration.id())
                            .status("SUCCESS")
                            .message("Messenger setup completed successfully")
                            .createdAt(new Date())
                            .createdBy(userEmail)
                            .build());

        } catch (Exception e) {
            log.error("Error creating messenger setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to create messenger setup: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get Messenger Setup configuration by chatbot ID
     * GET /v1/api/chatbot/messenger/{chatbotId}
     */
    @GetMapping("/messenger/{chatbotId}")
    public ResponseEntity<MessengerIntegration> getMessengerSetup(@PathVariable String chatbotId) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Getting Messenger setup for chatbot: {} by user: {}", chatbotId, userEmail);

            return messengerIntegrationService
                    .getMessengerIntegrationByChatbotId(chatbotId, userEmail)
                    .map(integration -> {
                        log.info("Messenger integration found for chatbot: {}", chatbotId);
                        return ResponseEntity.ok(integration);
                    })
                    .orElseGet(() -> {
                        log.info("Messenger integration not found for chatbot: {}", chatbotId);
                        return ResponseEntity.notFound().build();
                    });

        } catch (Exception e) {
            log.error("Error getting messenger setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Toggle Messenger Integration enabled/disabled status
     * PUT /v1/api/chatbot/messenger/{chatbotId}/toggle
     */
    @PutMapping("/messenger/{chatbotId}/toggle")
    public ResponseEntity<ChatBotCreationResponse> toggleMessengerIntegration(
            @PathVariable String chatbotId,
            @RequestParam(defaultValue = "false") Boolean enabled) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Toggling messenger integration for chatbot: {} to {} by user: {}", chatbotId, enabled, userEmail);

            MessengerIntegration updatedIntegration = messengerIntegrationService
                    .toggleMessengerIntegrationStatus(chatbotId, enabled, userEmail);

            return ResponseEntity.ok(ChatBotCreationResponse.builder()
                    .id(updatedIntegration.id())
                    .status("SUCCESS")
                    .message("Messenger integration " + (enabled ? "enabled" : "disabled") + " successfully")
                    .createdAt(new Date())
                    .createdBy(userEmail)
                    .build());

        } catch (Exception e) {
            log.error("Error toggling messenger integration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to toggle messenger integration: " + e.getMessage())
                            .build());
        }
    }
}
