package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    
    private String id;
    private String sessionId;
    private String userId;
    private String title;
    private List<ChatMessage> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isAnonymous;
    
    // Helper method to get first user message as title
    public String getAutoTitle() {
        if (messages != null && !messages.isEmpty()) {
            return messages.stream()
                    .filter(msg -> "user".equals(msg.getRole()))
                    .findFirst()
                    .map(msg -> {
                        String content = msg.getContent();
                        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
                    })
                    .orElse("New Chat");
        }
        return "New Chat";
    }
}
