package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopChatBot {
    private String chatBotId;
    private String chatBotName;
    private String chatBotTitle;
    private long conversationCount;
    private long messageCount;
    private long uniqueUsers;
    private String status;
    private String createdBy;
}

