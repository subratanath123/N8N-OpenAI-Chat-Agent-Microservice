package net.ai.chatbot.dto.aichatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to toggle chatbot status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotToggleRequest {
    
    /**
     * Status: "ACTIVE" | "DISABLED"
     */
    private String status;
}
