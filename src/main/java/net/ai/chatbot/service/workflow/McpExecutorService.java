package net.ai.chatbot.service.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.mcp.McpExecuteRequest;
import net.ai.chatbot.dto.mcp.McpExecuteResponse;
import net.ai.chatbot.entity.WorkflowConfig.ActionEndpoint;
import net.ai.chatbot.entity.WorkflowConfig.ActionParam;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
        this.restTemplate = configureTimeout(restTemplate);
        this.objectMapper = new ObjectMapper();
    }

    /** Apply 10-second connect + read timeout to the MCP rest template */
    private RestTemplate configureTimeout(RestTemplate rt) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);
        rt.setRequestFactory(factory);
        return rt;
    }

    // ─── Execute ──────────────────────────────────────────────────────────

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
                    request.getUserToken(), request.getMessage(), chatbotId);

            HttpMethod httpMethod = HttpMethod.valueOf(action.getMethod());
            ResponseEntity<String> response = restTemplate.exchange(
                    action.getUrl(), httpMethod, new HttpEntity<>(body, headers), String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            long durationMs = Instant.now().toEpochMilli() - start;
            // Audit log — no payload, no userToken
            log.info("MCP action audit: chatbotId={} actionId={} statusCode={} durationMs={}",
                    chatbotId, actionId, response.getStatusCode().value(), durationMs);

            Object responseBody = parseBody(response.getBody());
            String userMessage = resolveUserMessage(action, response.getBody(), success);

            return McpExecuteResponse.builder()
                    .success(success)
                    .statusCode(response.getStatusCode().value())
                    .message(userMessage)
                    .responseBody(responseBody)
                    .build();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            long durationMs = Instant.now().toEpochMilli() - start;
            log.error("MCP action audit: chatbotId={} actionId={} statusCode={} durationMs={}",
                    chatbotId, actionId, e.getStatusCode().value(), durationMs);
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
                    .error("Connection timed out after 10 seconds")
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

    // ─── Response Mode ────────────────────────────────────────────────────

    /**
     * "dynamic" → extract responsePath from upstream JSON, fall back to successMessage.
     * "static"  → always return successMessage.
     */
    private String resolveUserMessage(ActionEndpoint action, String responseBody, boolean success) {
        if (!success) return action.getFailureMessage();

        if ("dynamic".equals(action.getResponseMode())) {
            String extracted = extractPath(responseBody, action.getResponsePath());
            return (extracted != null && !extracted.isBlank()) ? extracted : action.getSuccessMessage();
        }
        return action.getSuccessMessage();
    }

    /**
     * Traverses a dot-path (e.g. "data.reply") into a parsed JSON response.
     */
    private String extractPath(String responseBody, String dotPath) {
        if (responseBody == null || dotPath == null || dotPath.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            for (String key : dotPath.split("\\.")) {
                root = root.path(key);
                if (root.isMissingNode()) return null;
            }
            return root.isTextual() ? root.asText() : root.toString();
        } catch (Exception e) {
            log.warn("extractPath failed for path '{}': {}", dotPath, e.getMessage());
            return null;
        }
    }

    // ─── Body Template Interpolation ──────────────────────────────────────

    /**
     * Processing order:
     * 1. System variables: actionName, message, sessionId, userId, chatbotId, userToken
     * 2. Dot-notation: {{order.quantity}} → individual field value
     * 3. Object placeholder: {{order}} → full JSON
     * 4. Flat params: {{productId}} → string value
     */
    String interpolate(ActionEndpoint action, Map<String, Object> params,
                       String sessionId, String userId, String userToken,
                       String message, String chatbotId) {
        String template = action.getBodyTemplate();
        if (template == null || template.isBlank()) {
            return serializeToJson(params);
        }

        String out = template
                .replace("{{actionName}}", nvl(action.getName()))
                .replace("{{message}}",   nvl(params.get("message") instanceof String s ? s : message))
                .replace("{{sessionId}}", nvl(sessionId))
                .replace("{{userId}}",    nvl(userId))
                .replace("{{chatbotId}}", nvl(chatbotId))
                .replace("{{userToken}}", nvl(userToken)); // forwarded as-is, never logged

        // Pass 1: dot-notation {{obj.field}}
        for (ActionParam p : action.getParams()) {
            if (!"object".equals(p.getType())) continue;
            Object objVal = params.get(p.getName());
            if (!(objVal instanceof Map<?, ?> objMap)) continue;
            for (Map.Entry<?, ?> entry : objMap.entrySet()) {
                out = out.replace("{{" + p.getName() + "." + entry.getKey() + "}}",
                        String.valueOf(entry.getValue()));
            }
        }

        // Pass 2: object placeholder {{obj}} → full JSON
        for (ActionParam p : action.getParams()) {
            if (!"object".equals(p.getType())) continue;
            Object objVal = params.get(p.getName());
            if (objVal == null) continue;
            out = out.replace("{{" + p.getName() + "}}", serializeToJson(objVal));
        }

        // Pass 3: flat params {{paramName}}
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?>) continue;
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
            log.error("Failed to decrypt auth credential");
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
            return body;
        }
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
