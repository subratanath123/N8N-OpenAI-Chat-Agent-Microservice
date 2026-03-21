package net.ai.chatbot.mcp.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.mcp.McpExecuteRequest;
import net.ai.chatbot.dto.mcp.McpExecuteResponse;
import net.ai.chatbot.dto.mcp.McpToolsResponse;
import net.ai.chatbot.entity.WorkflowConfig.ActionEndpoint;
import net.ai.chatbot.service.workflow.McpExecutorService;
import net.ai.chatbot.service.workflow.WorkflowConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP JSON-RPC 2.0 server for workflow-based action tools.
 *
 * Endpoint: POST /mcp/workflow/{chatbotId}
 * Health:   GET  /mcp/workflow/{chatbotId}/health
 *
 * Any MCP-compatible agent can connect to this URL and:
 *   1. Call tools/list  → get the chatbot's configured action tools
 *   2. Call tools/call  → backend executes the business endpoint with collected params
 *
 * The tool list is generated dynamically from the chatbot's WorkflowConfig,
 * so adding/removing actions instantly updates what agents see.
 */
@RestController
@RequestMapping("/mcp/workflow/{chatbotId}")
@Slf4j
public class McpWorkflowRestController {

    private final WorkflowConfigService workflowConfigService;
    private final McpExecutorService mcpExecutorService;
    private final ObjectMapper objectMapper;

    public McpWorkflowRestController(WorkflowConfigService workflowConfigService,
                                     McpExecutorService mcpExecutorService) {
        this.workflowConfigService = workflowConfigService;
        this.mcpExecutorService = mcpExecutorService;
        this.objectMapper = new ObjectMapper();
    }

    // ─── JSON-RPC 2.0 dispatcher ──────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(
            @PathVariable String chatbotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {

        String jsonrpc = (String) request.get("jsonrpc");
        String method  = (String) request.get("method");
        Object id      = request.get("id");

        @SuppressWarnings("unchecked")
        Map<String, Object> params = request.get("params") instanceof Map<?, ?>
                ? (Map<String, Object>) request.get("params") : Map.of();

        log.info("MCP Workflow [{}] JSON-RPC: method='{}' id={}", chatbotId, method, id);

        if (!"2.0".equals(jsonrpc)) {
            return ok(error(id, -32600, "Invalid Request: jsonrpc must be '2.0'", null));
        }
        if (method == null || method.isBlank()) {
            return ok(error(id, -32600, "Invalid Request: method is required", null));
        }

        return switch (method) {
            case "initialize"              -> ok(success(id, buildInitializeResult(chatbotId)));
            case "tools/list", "tools.list" -> handleToolsList(chatbotId, id);
            case "tools/call", "tools.call" -> handleToolsCall(chatbotId, id, params);
            default -> ok(error(id, -32601, "Method not found: " + method, null));
        };
    }

    // ─── initialize ───────────────────────────────────────────────────────

    private Map<String, Object> buildInitializeResult(String chatbotId) {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of(
                        "name", "Workflow MCP Server",
                        "version", "1.0.0",
                        "chatbotId", chatbotId
                )
        );
    }

    // ─── tools/list ───────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> handleToolsList(String chatbotId, Object id) {
        try {
            McpToolsResponse toolsResponse = workflowConfigService.getTools(chatbotId);
            List<Map<String, Object>> tools = new ArrayList<>();

            for (McpToolsResponse.Tool tool : toolsResponse.getTools()) {
                McpToolsResponse.Function fn = tool.getFunction();
                Map<String, Object> mcpTool = new LinkedHashMap<>();
                mcpTool.put("name", fn.getName());
                mcpTool.put("description", fn.getDescription());
                // MCP uses "inputSchema"; content mirrors the OpenAI "parameters" schema
                mcpTool.put("inputSchema", toInputSchema(fn.getParameters()));
                tools.add(mcpTool);
            }

            log.info("MCP Workflow [{}] tools/list: {} tools", chatbotId, tools.size());
            return ok(success(id, Map.of("tools", tools)));

        } catch (Exception e) {
            log.error("MCP Workflow [{}] tools/list error: {}", chatbotId, e.getMessage());
            return ok(error(id, -32603, "Failed to list tools: " + e.getMessage(), null));
        }
    }

    private Map<String, Object> toInputSchema(McpToolsResponse.ParameterSchema schema) {
        if (schema == null) {
            return Map.of("type", "object", "properties", Map.of(), "required", List.of());
        }
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", schema.getProperties() != null ? schema.getProperties() : Map.of());
        inputSchema.put("required", schema.getRequired() != null ? schema.getRequired() : List.of());
        return inputSchema;
    }

    // ─── tools/call ───────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> handleToolsCall(
            String chatbotId, Object id, Map<String, Object> params) {

        String toolName = (String) params.get("name");

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?>
                ? (Map<String, Object>) params.get("arguments") : Map.of();

        if (toolName == null || toolName.isBlank()) {
            return ok(error(id, -32602, "Missing required param: name", null));
        }

        log.info("MCP Workflow [{}] tools/call: tool='{}' args={}", chatbotId, toolName, arguments.keySet());

        try {
            // Resolve action by tool name, get its id for the executor
            ActionEndpoint action = workflowConfigService.findActionByToolName(chatbotId, toolName);

            McpExecuteRequest execRequest = McpExecuteRequest.builder()
                    .actionId(action.getId())
                    .collectedParams(arguments)
                    .sessionId(stringArg(arguments, "sessionId"))
                    .userId(stringArg(arguments, "userId"))
                    .message(stringArg(arguments, "message"))
                    .build();

            McpExecuteResponse execResponse = mcpExecutorService.execute(chatbotId, execRequest);

            // Build MCP content response
            String text = execResponse.isSuccess()
                    ? buildSuccessText(execResponse)
                    : buildFailureText(execResponse);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of("type", "text", "text", text)));
            result.put("isError", !execResponse.isSuccess());
            result.put("success", execResponse.isSuccess());
            result.put("statusCode", execResponse.getStatusCode());
            result.put("message", execResponse.getMessage());
            if (execResponse.getResponseBody() != null) {
                result.put("responseBody", execResponse.getResponseBody());
            }
            if (!execResponse.isSuccess() && execResponse.getError() != null) {
                result.put("error", execResponse.getError());
            }

            return ok(success(id, result));

        } catch (IllegalArgumentException e) {
            return ok(error(id, -32602, e.getMessage(), null));
        } catch (Exception e) {
            log.error("MCP Workflow [{}] tools/call '{}' failed: {}", chatbotId, toolName, e.getMessage(), e);
            return ok(error(id, -32603, "Tool execution failed: " + e.getMessage(), null));
        }
    }

    private String buildSuccessText(McpExecuteResponse r) {
        StringBuilder sb = new StringBuilder();
        if (r.getMessage() != null) sb.append(r.getMessage()).append("\n\n");
        if (r.getResponseBody() != null) {
            try {
                sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r.getResponseBody()));
            } catch (Exception e) {
                sb.append(r.getResponseBody().toString());
            }
        }
        return sb.toString().trim();
    }

    private String buildFailureText(McpExecuteResponse r) {
        StringBuilder sb = new StringBuilder();
        if (r.getMessage() != null) sb.append(r.getMessage());
        if (r.getError() != null) sb.append("\n\nError: ").append(r.getError());
        return sb.toString().trim();
    }

    // ─── Health ───────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(@PathVariable String chatbotId) {
        try {
            McpToolsResponse tools = workflowConfigService.getTools(chatbotId);
            List<String> toolNames = tools.getTools().stream()
                    .map(t -> t.getFunction().getName())
                    .toList();
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "Workflow MCP Server",
                    "chatbotId", chatbotId,
                    "availableTools", toolNames,
                    "toolCount", toolNames.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "Workflow MCP Server",
                    "chatbotId", chatbotId,
                    "availableTools", List.of(),
                    "note", "No workflow configured"
            ));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v instanceof String s ? s : null;
    }

    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> success(Object id, Map<String, Object> result) {
        return Map.of("jsonrpc", "2.0", "result", result, "id", id != null ? id : 0);
    }

    private Map<String, Object> error(Object id, int code, String message, Object data) {
        Map<String, Object> err = data != null
                ? Map.of("code", code, "message", message, "data", data)
                : Map.of("code", code, "message", message);
        return Map.of("jsonrpc", "2.0", "error", err, "id", id != null ? id : 0);
    }
}
