package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "n8n_chat_session_histories") // Adjust collection name as needed
public class UserChatHistory {
    
    @Id
    private String id;
    
    private String email;
    private String conversationid;
    private String chatbotId;
    private String userMessage;
    private Instant createdAt;
    private String aiMessage;
    private String mode;
    private boolean isAnonymous;
    private String role; // "user" or "assistant"
    private String senderType; // "user", "bot", "admin_reply"
    private String adminUserId; // Email of admin who sent reply
    private String status; // "sent", "delivered", etc.

}
