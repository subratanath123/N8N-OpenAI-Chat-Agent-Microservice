package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "chatbot_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotType {

    @Id
    private String id;

    private String name; // e.g., "General AI Agent", "Customer Support Agent", "Sales Agent"
    
    private String role; // The role description for the chatbot
    
    private String persona; // The persona/characteristics of the chatbot
    
    private String constraints; // Constraints or limitations for the chatbot
    
    private Date createdAt;
    private Date updatedAt;
}

