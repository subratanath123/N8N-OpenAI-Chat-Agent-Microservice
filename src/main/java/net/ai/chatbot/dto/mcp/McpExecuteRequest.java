package net.ai.chatbot.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request body for POST /v1/api/mcp/{chatbotId}/execute
 * Called by N8N after the LLM has collected all required parameter values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpExecuteRequest {
    /** Matches ActionEndpoint.id */
    private String actionId;
    /** Parameter values collected by LLM — may contain nested objects */
    private Map<String, Object> collectedParams;
    private String sessionId;
    private String userId;
    /**
     * Auth token from the embedding website's widget init.
     * Forwarded as-is to the action endpoint via {{userToken}}.
     * Never validated or stored by the backend.
     */
    private String userToken;
    private String message;
}
