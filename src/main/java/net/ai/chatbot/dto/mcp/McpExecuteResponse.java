package net.ai.chatbot.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from POST /v1/api/mcp/{chatbotId}/execute
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpExecuteResponse {
    private boolean success;
    private int statusCode;
    /** Human-readable message for the LLM to relay to the user */
    private String message;
    /** Raw response body from the action endpoint */
    private Object responseBody;
    /** Error detail — present only when success = false */
    private String error;
}
