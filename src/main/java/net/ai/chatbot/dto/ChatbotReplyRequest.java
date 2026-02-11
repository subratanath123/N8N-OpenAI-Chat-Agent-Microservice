package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for chatbot reply API endpoint
 * Used when admins/managers send replies on behalf of chatbot
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotReplyRequest {
    
    @NotBlank(message = "conversationId is required")
    private String conversationId;
    
    @NotBlank(message = "chatbotId is required")
    private String chatbotId;
    
    @NotBlank(message = "message is required")
    @Size(max = 10000, message = "Message exceeds maximum length of 10000 characters")
    private String message;
    
    @NotBlank(message = "role is required")
    @Pattern(regexp = "^assistant$", message = "Role must be 'assistant'")
    private String role;
}

