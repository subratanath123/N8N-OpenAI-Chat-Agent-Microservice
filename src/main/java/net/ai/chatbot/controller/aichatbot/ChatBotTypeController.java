package net.ai.chatbot.controller.aichatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotTypeDao;
import net.ai.chatbot.dto.aichatbot.ChatBotTypeResponse;
import net.ai.chatbot.entity.ChatBotType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/chatbot/types")
public class ChatBotTypeController {

    private final ChatBotTypeDao chatBotTypeDao;

    public ChatBotTypeController(ChatBotTypeDao chatBotTypeDao) {
        this.chatBotTypeDao = chatBotTypeDao;
    }

    /**
     * Get all chatbot types
     * GET /v1/api/chatbot/types
     */
    @GetMapping
    public ResponseEntity<List<ChatBotTypeResponse>> getAllChatBotTypes() {
        try {
            log.info("Retrieving all chatbot types");

            List<ChatBotType> chatBotTypes = chatBotTypeDao.findAllByOrderByNameAsc();

            List<ChatBotTypeResponse> responses = chatBotTypes.stream()
                    .map(type -> ChatBotTypeResponse.builder()
                            .id(type.getId())
                            .name(type.getName())
                            .role(type.getRole())
                            .persona(type.getPersona())
                            .constraints(type.getConstraints())
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error retrieving chatbot types", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    /**
     * Get chatbot type by ID
     * GET /v1/api/chatbot/types/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChatBotTypeResponse> getChatBotType(@PathVariable String id) {
        try {
            log.info("Retrieving chatbot type: {}", id);

            ChatBotType chatBotType = chatBotTypeDao.findById(id)
                    .orElse(null);

            if (chatBotType == null) {
                return ResponseEntity.notFound().build();
            }

            ChatBotTypeResponse response = ChatBotTypeResponse.builder()
                    .id(chatBotType.getId())
                    .name(chatBotType.getName())
                    .role(chatBotType.getRole())
                    .persona(chatBotType.getPersona())
                    .constraints(chatBotType.getConstraints())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving chatbot type: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

