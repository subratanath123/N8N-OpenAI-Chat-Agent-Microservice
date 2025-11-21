package net.ai.chatbot.controller.aichatbot;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationRequest;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.KnowledgeBase;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/chatbot")
public class AIChatBotController {

    private final ChatBotService chatBotService;

    public AIChatBotController(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
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
                    .map(chatbot -> ChatBotCreationResponse.builder()
                            .id(chatbot.getId())
                            .name(chatbot.getName())
                            .title(chatbot.getTitle())
                            .createdAt(chatbot.getCreatedAt())
                            .createdBy(chatbot.getCreatedBy())
                            .status(chatbot.getStatus())
                            .message("Chatbot retrieved")
                            .build())
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

        var chatbot = chatBotService.getChatBot(id);

        if (chatbot == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(chatbot);
    }

    /**
     * Get knowledgebase by ID
     * GET /v1/api/chatbot//{id}/knowledgebase/list
     */
    @GetMapping("/{id}/knowledge-bases")
    public List<KnowledgeBase> getKnowledgebaseList(@PathVariable String id) {
        log.info("Getting chatbot knowledge base list: {}", id);
        return chatBotService.getKnowledgeBaseList(id);
    }

    /**
     * Update chatbot configuration
     * PUT /v1/api/chatbot/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ChatBot> updateChatBot(@PathVariable String id,
                                                 @Valid @RequestBody ChatBotCreationRequest request) {
        log.info("Updating chatbot: {}", id);

        ChatBot chatBot = chatBotService.updateChatBot(id, request);

        return ResponseEntity.ok(chatBot);
    }
}

