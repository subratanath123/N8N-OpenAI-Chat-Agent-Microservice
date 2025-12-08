package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivity {
    private String email;
    private long conversationCount;
    private long messageCount;
    private long chatBotsCreated;
    private String lastActivityDate;
}

