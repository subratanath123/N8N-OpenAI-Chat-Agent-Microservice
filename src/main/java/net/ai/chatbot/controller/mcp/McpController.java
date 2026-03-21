package net.ai.chatbot.controller.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.mcp.McpDetectTriggersRequest;
import net.ai.chatbot.dto.mcp.McpDetectTriggersResponse;
import net.ai.chatbot.dto.mcp.McpExecuteRequest;
import net.ai.chatbot.dto.mcp.McpExecuteResponse;
import net.ai.chatbot.dto.mcp.McpToolsResponse;
import net.ai.chatbot.service.workflow.McpExecutorService;
import net.ai.chatbot.service.workflow.WorkflowConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP endpoints called by N8N during chatbot interaction.
 *
 * Flow:
 *   1. N8N calls GET /tools  → gets tool list → passes to LLM as function definitions
 *   2. LLM collects params from user, decides tool call
 *   3. N8N calls POST /execute → backend calls business endpoint → result back to LLM
 *   4. LLM generates final reply → user
 *
 * Optional fast path (no LLM needed):
 *   - N8N calls POST /detect-triggers for keyword matching before sending to LLM
 */
@RestController
@RequestMapping("/v1/api/mcp/{chatbotId}")
@RequiredArgsConstructor
@Slf4j
public class McpController {

    private final WorkflowConfigService workflowConfigService;
    private final McpExecutorService mcpExecutorService;

    /**
     * GET /v1/api/mcp/{chatbotId}/tools
     * Returns OpenAI function-calling compatible tool definitions.
     * N8N passes these to the LLM before the conversation starts.
     */
    @GetMapping("/tools")
    public ResponseEntity<McpToolsResponse> getTools(@PathVariable String chatbotId) {
        return ResponseEntity.ok(workflowConfigService.getTools(chatbotId));
    }

    /**
     * POST /v1/api/mcp/{chatbotId}/execute
     * Executes an action after the LLM has collected all required parameter values.
     * Backend decrypts credentials, interpolates body template, calls business endpoint.
     */
    @PostMapping("/execute")
    public ResponseEntity<?> execute(@PathVariable String chatbotId,
                                     @RequestBody McpExecuteRequest request) {
        if (request.getActionId() == null || request.getActionId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "actionId is required"));
        }
        try {
            McpExecuteResponse response = mcpExecutorService.execute(chatbotId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("MCP execute error for chatbot {}: {}", chatbotId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Tool execution failed: " + e.getMessage()));
        }
    }

    /**
     * POST /v1/api/mcp/{chatbotId}/detect-triggers
     * Keyword-based fast path — N8N can call this before the LLM to detect clear intents.
     * Returns matched action names if any trigger phrase matches the user's message.
     */
    @PostMapping("/detect-triggers")
    public ResponseEntity<McpDetectTriggersResponse> detectTriggers(
            @PathVariable String chatbotId,
            @RequestBody McpDetectTriggersRequest request) {

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.ok(McpDetectTriggersResponse.builder()
                    .triggered(false).matchedTools(List.of()).message(request.getMessage()).build());
        }

        List<String> matched = workflowConfigService.detectTriggers(chatbotId, request.getMessage());
        return ResponseEntity.ok(McpDetectTriggersResponse.builder()
                .triggered(!matched.isEmpty())
                .matchedTools(matched)
                .message(request.getMessage())
                .build());
    }
}
