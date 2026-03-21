package net.ai.chatbot.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpDetectTriggersResponse {
    /** Whether any trigger phrase matched */
    private boolean triggered;
    /** Names of matched action tools */
    private List<String> matchedTools;
    /** Original message */
    private String message;
}
