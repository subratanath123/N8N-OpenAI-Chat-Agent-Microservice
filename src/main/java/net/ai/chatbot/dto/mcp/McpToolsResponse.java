package net.ai.chatbot.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP-compatible tool list returned to N8N.
 * N8N passes this to the LLM as the available function-calling tools.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolsResponse {
    private String chatbotId;
    private List<Tool> tools;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        @Builder.Default
        private String type = "function";
        private Function function;
        /**
         * MCP client extension: how this action authenticates upstream (authType, requiresUserToken, etc.).
         */
        private Map<String, Object> auth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        private String name;
        private String description;
        private ParameterSchema parameters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterSchema {
        @Builder.Default
        private String type = "object";
        private Map<String, Object> properties;
        private List<String> required;
    }
}
