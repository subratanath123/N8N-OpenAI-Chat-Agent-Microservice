package net.ai.chatbot.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.mcp.McpExecuteRequest;
import net.ai.chatbot.dto.mcp.McpExecuteResponse;
import net.ai.chatbot.entity.WorkflowConfig.ActionEndpoint;
import net.ai.chatbot.entity.WorkflowConfig.ActionParam;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class McpExecutorService {

    private final WorkflowConfigService workflowConfigService;
    private final EncryptionUtils encryptionUtils;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public McpExecutorService(WorkflowConfigService workflowConfigService,
                               EncryptionUtils encryptionUtils,
                               @Qualifier("mcpRestTemplate") RestTemplate restTemplate) {
        this.workflowConfigService = workflowConfigService;
        this.encryptionUtils = encryptionUtils;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public McpExecuteResponse execute(String chatbotId, McpExecuteRequest request) {
        String actionId = request.getActionId();
        Map<String, Object> params = request.getCollectedParams() != null
                ? request.getCollectedParams() : Map.of();

        log.info("Executing MCP action '{}' for chatbot '{}'", actionId, chatbotId);
        long start = Instant.now().toEpochMilli();

        ActionEndpoint action = workflowConfigService.findActionById(chatbotId, actionId);

        try {
            HttpHeaders headers = buildAuthHeaders(action);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = interpolate(action, params,
                    request.getSessionId(), request.getUserId(),
                    request.getMessage(), chatbotId);

            HttpMethod httpMethod = HttpMethod.valueOf(action.getMethod());
            ResponseEntity<String> response = restTemplate.exchange(
                    action.getUrl(), httpMethod, new HttpEntity<>(body, headers), String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            long durationMs = Instant.now().toEpochMilli() - start;
            log.info("MCP action '{}' completed: status={} duration={}ms", actionId, response.getStatusCode(), durationMs);

            Object responseBody = parseBody(response.getBody());
            return McpExecuteResponse.builder()
                    .success(success)
                    .statusCode(response.getStatusCode().value())
                    .message(success ? action.getSuccessMessage() : action.getFailureMessage())
                    .responseBody(responseBody)
                    .build();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            long durationMs = Instant.now().toEpochMilli() - start;
            log.error("MCP action '{}' HTTP error: {} duration={}ms body={}",
                    actionId, e.getStatusCode(), durationMs, e.getResponseBodyAsString());
            return McpExecuteResponse.builder()
                    .success(false)
                    .statusCode(e.getStatusCode().value())
                    .message(action.getFailureMessage())
                    .error("Upstream returned HTTP " + e.getStatusCode().value())
                    .responseBody(parseBody(e.getResponseBodyAsString()))
                    .build();

        } catch (ResourceAccessException e) {
            log.error("MCP action '{}' timeout/connection error: {}", actionId, e.getMessage());
            return McpExecuteResponse.builder()
                    .success(false)
                    .statusCode(504)
                    .message(action.getFailureMessage())
                    .error("Connection failed or timed out: " + e.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("MCP action '{}' unexpected error: {}", actionId, e.getMessage(), e);
            return McpExecuteResponse.builder()
                    .success(false)
                    .statusCode(500)
                    .message(action.getFailureMessage())
                    .error("Action execution failed: " + e.getMessage())
                    .build();
        }
    }

    // ─── Body Template Interpolation ──────────────────────────────────────

    /**
     * Three-pass interpolation:
     *  1. Standard variables (actionName, sessionId, userId, chatbotId, message)
     *  2. Dot-notation: {{obj.field}} → individual value
     *  3. Object placeholder: {{obj}} → full JSON
     *  4. Flat params: {{paramName}} → string value
     */
    String interpolate(ActionEndpoint action, Map<String, Object> params,
                       String sessionId, String userId, String message, String chatbotId) {
        String template = action.getBodyTemplate();
        if (template == null || template.isBlank()) {
            return serializeToJson(params);
        }

        String out = template
                .replace("{{actionName}}", action.getName() != null ? action.getName() : "")
                .replace("{{chatbotId}}", chatbotId != null ? chatbotId : "")
                .replace("{{sessionId}}", sessionId != null ? sessionId : "")
                .replace("{{userId}}", userId != null ? userId : "")
                .replace("{{message}}", message != null ? message : "");

        // Pass 1: dot-notation {{obj.field}}
        for (ActionParam p : action.getParams()) {
            if (!"object".equals(p.getType())) continue;
            Object objVal = params.get(p.getName());
            if (!(objVal instanceof Map<?, ?> objMap)) continue;
            for (Map.Entry<?, ?> entry : objMap.entrySet()) {
                String placeholder = "{{" + p.getName() + "." + entry.getKey() + "}}";
                out = out.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        // Pass 2: object placeholder {{obj}} → full JSON (no surrounding quotes in template)
        for (ActionParam p : action.getParams()) {
            if (!"object".equals(p.getType())) continue;
            Object objVal = params.get(p.getName());
            if (objVal == null) continue;
            String json = serializeToJson(objVal);
            out = out.replace("{{" + p.getName() + "}}", json);
        }

        // Pass 3: flat params {{paramName}}
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?>) continue; // already handled
            out = out.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
        }

        return out;
    }

    // ─── Auth Headers ─────────────────────────────────────────────────────

    private HttpHeaders buildAuthHeaders(ActionEndpoint action) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        String authType = action.getAuthType();
        if (authType == null || authType.equals("none")) return headers;

        String decrypted = decrypt(action.getAuthValue());
        if (decrypted == null) return headers;

        switch (authType) {
            case "bearer" -> headers.set("Authorization", "Bearer " + decrypted);
            case "apikey" -> {
                String headerName = action.getApiKeyHeader() != null
                        && !action.getApiKeyHeader().isBlank()
                        ? action.getApiKeyHeader() : "X-API-Key";
                headers.set(headerName, decrypted);
            }
            case "basic" -> {
                String encoded = Base64.getEncoder().encodeToString(decrypted.getBytes());
                headers.set("Authorization", "Basic " + encoded);
            }
        }
        return headers;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) return null;
        try {
            return encryptionUtils.decrypt(encryptedValue);
        } catch (Exception e) {
            log.error("Failed to decrypt auth credential: {}", e.getMessage());
            return null;
        }
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Object parseBody(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception e) {
            return body; // return raw string if not valid JSON
        }
    }
}
