package net.ai.chatbot.controller.aichatbot;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.aichatbot.*;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.KnowledgeBase;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import net.ai.chatbot.service.googlecalendar.ChatbotOwnershipService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/chatbot")
public class AIChatBotController {

    private final ChatBotService chatBotService;
    private final ChatbotOwnershipService chatbotOwnershipService;

    public AIChatBotController(ChatBotService chatBotService, ChatbotOwnershipService chatbotOwnershipService) {
        this.chatBotService = chatBotService;
        this.chatbotOwnershipService = chatbotOwnershipService;
    }

    /**
     * Create a new chatbot configuration
     * POST /v1/api/chatbot/create
     */
    @PostMapping("/create")
    public ResponseEntity<ChatBotCreationResponse> createChatBot(
            @Valid @RequestBody ChatBotCreationRequest request) {
        try {
            log.info("Creating chatbot: {}", request.getName());

            // Process chatbot creation using service
            String chatbotId = chatBotService.createChatBot(request, AuthUtils.getEmail());

            // Create response
            ChatBotCreationResponse response = ChatBotCreationResponse.builder()
                    .id(chatbotId)
                    .title(request.getTitle())
                    .name(request.getName())
                    .createdAt(new Date())
                    .createdBy(AuthUtils.getEmail())
                    .status("SUCCESS")
                    .message("Chatbot created successfully")
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error creating chatbot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to create chatbot: " + e.getMessage())
                            .build());
        }
    }

    /**
     * List all chatbots for the authenticated user
     * GET /v1/api/chatbot/list
     */
    @GetMapping("/list")
    public ResponseEntity<List<ChatBotCreationResponse>> listChatBots() {
        try {
            log.info("Listing chatbots for user: {}", AuthUtils.getEmail());

            List<ChatBot> chatbots = chatBotService.getChatBotsByUser(AuthUtils.getEmail());

            List<ChatBotCreationResponse> responses = chatbots.stream()
                    .map(chatbot -> {
                        // Get stats for this chatbot
                        ChatBotStatsItemResponse stats = chatBotService.getChatBotStatsById(chatbot.getId());
                        
                        return ChatBotCreationResponse.builder()
                                .id(chatbot.getId())
                                .name(chatbot.getName())
                                .title(chatbot.getTitle())
                                .createdAt(chatbot.getCreatedAt())
                                .createdBy(chatbot.getCreatedBy())
                                .status(chatbot.getStatus())
                                .message("Chatbot retrieved")
                                .totalConversations(stats.getTotalConversations())
                                .totalMessages(stats.getTotalMessages())
                                .canConfigure(chatbotOwnershipService.canConfigureChatbot(chatbot.getId(), AuthUtils.getEmail()))
                                .build();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error listing chatbots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    /**
     * Get chatbot by ID
     * GET /v1/api/chatbot/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChatBot> getChatBot(@PathVariable String id) {
        log.info("Getting chatbot: {}", id);

        try {
            chatbotOwnershipService.verifyCanView(id, AuthUtils.getEmail());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var chatbot = chatBotService.getChatBot(id);

        if (chatbot == null) {
            return ResponseEntity.notFound().build();
        }

        chatbot.setCanConfigure(chatbotOwnershipService.canConfigureChatbot(id, AuthUtils.getEmail()));

        return ResponseEntity.ok(chatbot);
    }

    /**
     * Get knowledgebase by ID
     * GET /v1/api/chatbot//{id}/knowledgebase/list
     */
    @GetMapping("/{id}/knowledge-bases")
    public ResponseEntity<List<KnowledgeBase>> getKnowledgebaseList(@PathVariable String id) {
        log.info("Getting chatbot knowledge base list: {}", id);
        try {
            chatbotOwnershipService.verifyCanView(id, AuthUtils.getEmail());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(chatBotService.getKnowledgeBaseList(id));
    }

    /**
     * Update chatbot configuration
     * PUT /v1/api/chatbot/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ChatBot> updateChatBot(@PathVariable String id,
                                                 @Valid @RequestBody ChatBotCreationRequest request) {
        log.info("Updating chatbot: {}", id);

        try {
            chatbotOwnershipService.verifyCanConfigure(id, AuthUtils.getEmail());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ChatBot chatBot = chatBotService.updateChatBot(id, request);

        return ResponseEntity.ok(chatBot);
    }

    /**
     * Delete chatbot
     * DELETE /v1/api/chatbot/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteChatBot(@PathVariable String id) {
        try {
            log.info("Deleting chatbot: {} for user: {}", id, AuthUtils.getEmail());

            try {
                chatbotOwnershipService.verifyOwnership(id, AuthUtils.getEmail());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "error", "Not Found",
                                "message", e.getMessage()
                        ));
            } catch (SecurityException e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "success", false,
                                "error", "Forbidden",
                                "message", "Only the owner can delete this chatbot"
                        ));
            }

            chatBotService.deleteChatBot(id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Chatbot deleted successfully",
                    "id", id
            ));

        } catch (IllegalArgumentException e) {
            log.error("Chatbot not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "error", "Not Found",
                            "message", e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Error deleting chatbot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Internal Server Error",
                            "message", "Failed to delete chatbot: " + e.getMessage()
                    ));
        }
    }

    /**
     * Toggle chatbot status (enable/disable)
     * PUT /v1/api/chatbot/{id}/toggle
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleChatBotStatus(
            @PathVariable String id,
            @Valid @RequestBody ChatBotToggleRequest request) {
        try {
            log.info("Toggling chatbot status: {} to {} for user: {}", id, request.getStatus(), AuthUtils.getEmail());

            try {
                chatbotOwnershipService.verifyCanConfigure(id, AuthUtils.getEmail());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "error", "Not Found",
                                "message", e.getMessage()
                        ));
            } catch (SecurityException e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "success", false,
                                "error", "Forbidden",
                                "message", e.getMessage()
                        ));
            }

            ChatBot chatBot = chatBotService.toggleChatBotStatus(id, request.getStatus());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Chatbot status updated successfully",
                    "id", chatBot.getId(),
                    "status", chatBot.getStatus()
            ));

        } catch (IllegalArgumentException e) {
            log.error("Error toggling chatbot status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", "Bad Request",
                            "message", e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Error toggling chatbot status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Internal Server Error",
                            "message", "Failed to toggle chatbot status: " + e.getMessage()
                    ));
        }
    }

    /**
     * Get aggregated chatbot statistics
     * GET /v1/api/chatbot/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ChatBotStatsResponse> getChatBotStats() {
        try {
            log.info("Getting chatbot statistics for user: {}", AuthUtils.getEmail());

            ChatBotStatsResponse stats = chatBotService.getChatBotStats(AuthUtils.getEmail());

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting chatbot statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotStatsResponse.builder()
                            .totalChatbots(0L)
                            .totalConversations(0L)
                            .totalMessages(0L)
                            .activeDomains(0L)
                            .build());
        }
    }

    /**
     * List all chatbots with per-chatbot statistics
     * GET /v1/api/chatbot/list-with-stats
     */
    @GetMapping("/list-with-stats")
    public ResponseEntity<List<ChatBotListItemResponse>> listChatBotsWithStats() {
        try {
            log.info("Listing chatbots with stats for user: {}", AuthUtils.getEmail());

            List<ChatBotListItemResponse> chatbots = chatBotService.getChatBotsWithStats(AuthUtils.getEmail());

            return ResponseEntity.ok(chatbots);

        } catch (Exception e) {
            log.error("Error listing chatbots with stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    /**
     * Get statistics for a specific chatbot
     * GET /v1/api/chatbot/{chatbotId}/stats
     */
    @GetMapping("/{chatbotId}/stats")
    public ResponseEntity<ChatBotStatsItemResponse> getChatBotStatsById(@PathVariable String chatbotId) {
        try {
            log.info("Getting stats for chatbot: {} requested by user: {}", chatbotId, AuthUtils.getEmail());

            try {
                chatbotOwnershipService.verifyCanView(chatbotId, AuthUtils.getEmail());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.notFound().build();
            } catch (SecurityException e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            ChatBotStatsItemResponse stats = chatBotService.getChatBotStatsById(chatbotId);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting chatbot stats for {}: {}", chatbotId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotStatsItemResponse.builder()
                            .chatbotId(chatbotId)
                            .totalConversations(0L)
                            .totalMessages(0L)
                            .build());
        }
    }
}

