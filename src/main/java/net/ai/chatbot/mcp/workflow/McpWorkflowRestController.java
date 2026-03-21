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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP JSON-RPC 2.0 server for workflow-based action tools.
 *
 * Transport: HTTP + SSE (MCP spec 2024-11-05)
 *
 * Step 1 — N8N connects:
 *   GET /mcp/workflow/{chatbotId}/sse
 *   → Server sends SSE event: endpoint → /mcp/workflow/{chatbotId}/messages
 *
 * Step 2 — N8N sends messages:
 *   POST /mcp/workflow/{chatbotId}/messages  (JSON-RPC 2.0)
 *   → initialize, tools/list, tools/call
 *
 * Also supports direct POST (without SSE) for testing:
 *   POST /mcp/workflow/{chatbotId}
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

    // ─── SSE handshake (N8N connects here first) ──────────────────────────

    /**
     * GET /mcp/workflow/{chatbotId}/sse
     *
     * N8N MCP client connects to this endpoint to establish the SSE channel.
     * Server immediately sends an "endpoint" event telling N8N where to POST messages,
     * then keeps the connection alive.
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@PathVariable String chatbotId, HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout

        String messagesUrl = buildBaseUrl(httpRequest) + "/mcp/workflow/" + chatbotId + "/messages";
        log.info("MCP Workflow [{}] SSE connection established, messages endpoint: {}", chatbotId, messagesUrl);

        try {
            // MCP spec: send "endpoint" event with the POST URL for JSON-RPC messages
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data(messagesUrl));
        } catch (IOException e) {
            log.warn("MCP Workflow [{}] SSE send failed: {}", chatbotId, e.getMessage());
            emitter.completeWithError(e);
        }

        // Keep connection open; N8N will POST messages separately
        emitter.onTimeout(emitter::complete);
        emitter.onError(err -> log.debug("MCP Workflow [{}] SSE error: {}", chatbotId, err.getMessage()));

        return emitter;
    }

    // ─── JSON-RPC message handler (N8N POSTs here after SSE handshake) ────

    /**
     * POST /mcp/workflow/{chatbotId}/messages
     * The primary message endpoint advertised via SSE.
     */
    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> messages(
            @PathVariable String chatbotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        return dispatch(chatbotId, request);
    }

    /**
     * POST /mcp/workflow/{chatbotId}
     * Direct endpoint — useful for testing without SSE handshake.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(
            @PathVariable String chatbotId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        return dispatch(chatbotId, request);
    }

    // ─── JSON-RPC dispatcher ──────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> dispatch(String chatbotId, Map<String, Object> request) {
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
            case "initialize"               -> ok(success(id, buildInitializeResult(chatbotId)));
            case "notifications/initialized" -> ok(success(id, Map.of())); // ack
            case "tools/list", "tools.list"  -> handleToolsList(chatbotId, id);
            case "tools/call", "tools.call"  -> handleToolsCall(chatbotId, id, params);
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
            ActionEndpoint action = workflowConfigService.findActionByToolName(chatbotId, toolName);

            McpExecuteRequest execRequest = McpExecuteRequest.builder()
                    .actionId(action.getId())
                    .collectedParams(arguments)
                    .sessionId(stringArg(arguments, "sessionId"))
                    .userId(stringArg(arguments, "userId"))
                    .message(stringArg(arguments, "message"))
                    .build();

            McpExecuteResponse execResponse = mcpExecutorService.execute(chatbotId, execRequest);

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
                    "sseEndpoint", "/mcp/workflow/" + chatbotId + "/sse",
                    "messagesEndpoint", "/mcp/workflow/" + chatbotId + "/messages",
                    "availableTools", toolNames,
                    "toolCount", toolNames.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "Workflow MCP Server",
                    "chatbotId", chatbotId,
                    "note", "No workflow configured"
            ));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host   = request.getServerName();
        int port      = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                           || ("https".equals(scheme) && port == 443);
        String base = scheme + "://" + host + (defaultPort ? "" : ":" + port);
        String ctx  = request.getContextPath();
        return (ctx != null && !ctx.isBlank()) ? base + ctx : base;
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
