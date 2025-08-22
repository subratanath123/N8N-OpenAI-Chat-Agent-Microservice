package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.UserChatHistoryResponse;
import net.ai.chatbot.service.mongodb.UserChatHistoryService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api")
public class UserChatHistoryApiController {

    @Autowired
    private UserChatHistoryService userChatHistoryService;

    /**
     * Get chat history for authenticated user
     * GET /api/chat-history?limit=50&offset=0
     */
    @GetMapping("/chat-history")
    public ResponseEntity<UserChatHistoryResponse> getChatHistory(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        log.info("Received chat history request with limit: {}, offset: {}", limit, offset);

        try {
            // Extract user information from auth header
            String userEmail = AuthUtils.getEmail(); // Extract from JWT token

            // Get chat histories for user
            List<UserChatHistory> histories = userChatHistoryService.getUserChatHistory(userEmail, limit, offset);
            long totalCount = userChatHistoryService.countUserChatHistories(userEmail);

            log.info("Retrieved {} chat histories for user: {}", histories.size(), userEmail);

            return ResponseEntity.ok(UserChatHistoryResponse.success(histories, (int) totalCount, offset / limit, limit));

        } catch (Exception e) {
            log.error("Error retrieving chat history: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    UserChatHistoryResponse.error("Failed to retrieve chat history: " + e.getMessage())
            );
        }
    }

    /**
     * Get all messages from a specific conversation
     * GET /api/chat-history/{conversationId}/messages
     */
    @GetMapping("/chat-history/{conversationId}/messages")
    public ResponseEntity<UserChatHistoryResponse> getAllMessagesFromConversation(@PathVariable String conversationId) {

        log.info("Received get all messages request for conversation: {}", conversationId);

        try {
            // Extract user information from auth header
            String userEmail = AuthUtils.getEmail();

            // Get all messages from conversation
            List<UserChatHistory> messages = userChatHistoryService.getAllMessagesFromConversation(conversationId, userEmail);

            if (!messages.isEmpty()) {
                log.info("Retrieved {} messages from conversation: {} for user: {}", messages.size(), conversationId, userEmail);
                return ResponseEntity.ok(UserChatHistoryResponse.success(messages));
            } else {
                log.warn("No messages found for conversation: {} and user: {}", conversationId, userEmail);
                return ResponseEntity.status(404).body(
                        UserChatHistoryResponse.error("No messages found for this conversation")
                );
            }

        } catch (Exception e) {
            log.error("Error retrieving messages from conversation: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    UserChatHistoryResponse.error("Failed to retrieve messages: " + e.getMessage())
            );
        }
    }
}
