package net.ai.chatbot.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpDetectTriggersRequest {
    private String message;
    private String sessionId;
    private String userId;
}
