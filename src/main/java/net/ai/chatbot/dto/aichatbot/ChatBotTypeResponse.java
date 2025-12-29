package net.ai.chatbot.dto.aichatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotTypeResponse {
    
    private String id;
    private String name; // e.g., "General AI Agent", "Customer Support Agent", "Sales Agent"
    private String role; // The role description for the chatbot
    private String persona; // The persona/characteristics of the chatbot
    private String constraints; // Constraints or limitations for the chatbot
}

