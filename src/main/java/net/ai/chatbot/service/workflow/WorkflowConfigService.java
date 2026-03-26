package net.ai.chatbot.service.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.WorkflowConfigDao;
import net.ai.chatbot.dto.mcp.McpToolsResponse;
import net.ai.chatbot.dto.workflow.ActionEndpointDto;
import net.ai.chatbot.dto.workflow.ActionParamDto;
import net.ai.chatbot.dto.workflow.SubParamDto;
import net.ai.chatbot.dto.workflow.WorkflowConfigRequest;
import net.ai.chatbot.dto.workflow.WorkflowConfigResponse;
import net.ai.chatbot.entity.WorkflowConfig;
import net.ai.chatbot.entity.WorkflowConfig.ActionEndpoint;
import net.ai.chatbot.entity.WorkflowConfig.ActionParam;
import net.ai.chatbot.entity.WorkflowConfig.SubParam;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowConfigService {

    /**
     * Returned when no workflow exists or every action is disabled, so MCP clients (e.g. n8n)
     * always receive at least one tool from {@link #getTools(String)}.
     */
    public static final String WORKFLOW_PLACEHOLDER_TOOL_NAME = "workflow_configuration_status";

    private final WorkflowConfigDao workflowConfigDao;
    private final EncryptionUtils encryptionUtils;

    // ─── CRUD ─────────────────────────────────────────────────────────────

    public WorkflowConfigResponse getWorkflow(String chatbotId) {
        WorkflowConfig config = workflowConfigDao.findByChatbotId(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found for chatbot: " + chatbotId));
        return toResponse(config);
    }

    public WorkflowConfigResponse saveWorkflow(String chatbotId, String ownerId, WorkflowConfigRequest request) {
        WorkflowConfig existing = workflowConfigDao.findByChatbotId(chatbotId).orElse(null);
        Instant now = Instant.now();

        List<ActionEndpoint> actions = request.getActions().stream()
                .map(dto -> toEntity(dto, existing))
                .collect(Collectors.toList());

        WorkflowConfig config;
        if (existing != null) {
            existing.setOwnerId(ownerId);
            existing.setActions(actions);
            existing.setUpdatedAt(now);
            config = existing;
        } else {
            config = WorkflowConfig.builder()
                    .chatbotId(chatbotId)
                    .ownerId(ownerId)
                    .actions(actions)
                    .updatedAt(now)
                    .build();
        }

        WorkflowConfig saved = workflowConfigDao.save(config);
        log.info("Workflow saved for chatbot {}: {} actions", chatbotId, actions.size());
        return toResponse(saved);
    }

    // ─── MCP Tool Registry ────────────────────────────────────────────────

    /**
     * Builds MCP-compatible (OpenAI function-calling) tool list from stored actions.
     * If there are no enabled actions, returns a single informational placeholder tool so
     * clients never see an empty tool list (n8n MCP Client fails on empty lists).
     */
    public McpToolsResponse getTools(String chatbotId) {
        WorkflowConfig config = workflowConfigDao.findByChatbotId(chatbotId).orElse(null);

        List<McpToolsResponse.Tool> tools;
        if (config == null) {
            tools = new ArrayList<>();
        } else {
            tools = config.getActions().stream()
                    .filter(ActionEndpoint::isEnabled)
                    .map(this::buildTool)
                    .collect(Collectors.toList());
        }

        if (tools.isEmpty()) {
            tools = List.of(buildWorkflowPlaceholderTool(chatbotId));
        }

        return McpToolsResponse.builder().chatbotId(chatbotId).tools(tools).build();
    }

    private McpToolsResponse.Tool buildWorkflowPlaceholderTool(String chatbotId) {
        Map<String, Object> noteProp = new LinkedHashMap<>();
        noteProp.put("type", "string");
        noteProp.put("description", "Optional; ignored. This is a placeholder until workflow actions exist.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("note", noteProp);

        String description = "No workflow HTTP actions are configured for chatbot "
                + chatbotId
                + " yet, or all actions are disabled. Add and enable actions in the app (AI Chatbots → "
                + "Workflow), then list tools again. Invoking this tool returns setup instructions.";

        return McpToolsResponse.Tool.builder()
                .type("function")
                .function(McpToolsResponse.Function.builder()
                        .name(WORKFLOW_PLACEHOLDER_TOOL_NAME)
                        .description(description)
                        .parameters(McpToolsResponse.ParameterSchema.builder()
                                .type("object")
                                .properties(properties)
                                .required(List.of())
                                .build())
                        .build())
                .build();
    }

    /**
     * Finds a single enabled action by its snake_case tool name (for MCP tools/call).
     * Tool name = action.name.toLowerCase().replace(" ", "_")
     */
    public ActionEndpoint findActionByToolName(String chatbotId, String toolName) {
        WorkflowConfig config = workflowConfigDao.findByChatbotId(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not configured for chatbot: " + chatbotId));

        return config.getActions().stream()
                .filter(ActionEndpoint::isEnabled)
                .filter(a -> a.getName() != null
                        && a.getName().toLowerCase().replace(" ", "_").equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tool not found or disabled: " + toolName));
    }

    /**
     * Finds a single enabled action by its id (for execute endpoint).
     */
    public ActionEndpoint findActionById(String chatbotId, String actionId) {
        WorkflowConfig config = workflowConfigDao.findByChatbotId(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not configured for chatbot: " + chatbotId));

        return config.getActions().stream()
                .filter(a -> a.getId() != null && a.getId().equals(actionId) && a.isEnabled())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Action not found or disabled: " + actionId));
    }

    /**
     * Fast-path keyword matching — splits triggerPhrases string on commas.
     */
    public List<String> detectTriggers(String chatbotId, String message) {
        WorkflowConfig config = workflowConfigDao.findByChatbotId(chatbotId).orElse(null);
        if (config == null) return List.of();

        String lower = message.toLowerCase();

        return config.getActions().stream()
                .filter(ActionEndpoint::isEnabled)
                .filter(action -> {
                    if (action.getTriggerPhrases() == null || action.getTriggerPhrases().isBlank()) return false;
                    return Arrays.stream(action.getTriggerPhrases().split(","))
                            .map(String::trim)
                            .anyMatch(phrase -> !phrase.isEmpty() && lower.contains(phrase.toLowerCase()));
                })
                .map(ActionEndpoint::getName)
                .collect(Collectors.toList());
    }

    // ─── MCP Schema Builder ───────────────────────────────────────────────

    private McpToolsResponse.Tool buildTool(ActionEndpoint action) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ActionParam p : action.getParams()) {
            if (p.getName() == null || p.getName().isBlank()) continue;
            properties.put(p.getName(), buildParamSchema(p));
            if (p.isRequired()) required.add(p.getName());
        }

        mergeMcpContextProperties(action, properties, required);

        String toolName = action.getName() != null
                ? action.getName().toLowerCase().replace(" ", "_")
                : action.getId();

        String description = action.getDescription() != null ? action.getDescription() : "";
        if (action.getTriggerPhrases() != null && !action.getTriggerPhrases().isBlank()) {
            description += " Trigger phrases: " + action.getTriggerPhrases() + ".";
        }
        Map<String, Object> authMeta = buildAuthMetadata(action);
        if (authMeta != null) {
            description += " [Auth: " + authMeta.get("type") + "; "
                    + (Boolean.TRUE.equals(authMeta.get("requiresUserToken"))
                    ? "pass userToken in arguments." : "server-side credentials.") + "]";
        }

        return McpToolsResponse.Tool.builder()
                .type("function")
                .function(McpToolsResponse.Function.builder()
                        .name(toolName)
                        .description(description)
                        .parameters(McpToolsResponse.ParameterSchema.builder()
                                .type("object")
                                .properties(properties)
                                .required(required)
                                .build())
                        .build())
                .auth(authMeta)
                .build();
    }

    /**
     * Adds standard MCP execution args and marks userToken required when auth is bound to {{userToken}}.
     */
    private void mergeMcpContextProperties(ActionEndpoint action,
                                           Map<String, Object> properties,
                                           List<String> required) {
        if (!properties.containsKey("sessionId")) {
            properties.put("sessionId", mcpStringProp("Conversation/session id for this chat."));
        }
        if (!properties.containsKey("userId")) {
            properties.put("userId", mcpStringProp("End-user id when available."));
        }
        if (!properties.containsKey("message")) {
            properties.put("message", mcpStringProp("Latest user message when invoking from chat."));
        }
        if (authRequiresUserToken(action) && !properties.containsKey("userToken")) {
            properties.put("userToken", mcpStringProp(
                    "Visitor token from the chat widget; required for this action's upstream auth."));
            if (!required.contains("userToken")) {
                required.add("userToken");
            }
        }
    }

    private Map<String, Object> mcpStringProp(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        return schema;
    }

    private Map<String, Object> buildAuthMetadata(ActionEndpoint action) {
        String authType = action.getAuthType();
        if (authType == null || "none".equalsIgnoreCase(authType)) {
            return null;
        }
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("type", authType.toLowerCase());
        boolean needsUser = authRequiresUserToken(action);
        auth.put("requiresUserToken", needsUser);
        if ("apikey".equalsIgnoreCase(authType)) {
            String h = action.getApiKeyHeader();
            if (h != null && !h.isBlank()) {
                auth.put("apiKeyHeader", h);
            }
        }
        auth.put("description", needsUser
                ? "Upstream auth uses the visitor token; pass userToken in tool arguments (from widget init)."
                : "Upstream auth uses a static credential stored on the server; userToken is not required.");
        return auth;
    }

    private boolean authRequiresUserToken(ActionEndpoint action) {
        String authType = action.getAuthType();
        if (authType == null || "none".equalsIgnoreCase(authType)) {
            return false;
        }
        String decrypted = decryptAuthForMeta(action.getAuthValue());
        return decrypted != null && decrypted.contains("{{userToken}}");
    }

    private String decryptAuthForMeta(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            return encryptionUtils.decrypt(encrypted);
        } catch (Exception e) {
            log.debug("Could not decrypt auth for MCP metadata: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildParamSchema(ActionParam p) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", p.getType() != null ? p.getType() : "string");
        schema.put("description", p.getDescription() != null ? p.getDescription() : "The " + p.getName());
        if (p.getExample() != null && !p.getExample().isBlank()) {
            schema.put("example", p.getExample());
        }

        if ("object".equals(p.getType()) && p.getProperties() != null && !p.getProperties().isEmpty()) {
            Map<String, Object> subProps = new LinkedHashMap<>();
            List<String> subRequired = new ArrayList<>();
            for (SubParam sp : p.getProperties()) {
                if (sp.getName() == null || sp.getName().isBlank()) continue;
                Map<String, Object> spSchema = new LinkedHashMap<>();
                spSchema.put("type", sp.getType() != null ? sp.getType() : "string");
                spSchema.put("description", sp.getDescription() != null ? sp.getDescription() : "The " + sp.getName());
                if (sp.getExample() != null && !sp.getExample().isBlank()) spSchema.put("example", sp.getExample());
                subProps.put(sp.getName(), spSchema);
                if (sp.isRequired()) subRequired.add(sp.getName());
            }
            schema.put("properties", subProps);
            if (!subRequired.isEmpty()) schema.put("required", subRequired);
        }

        return schema;
    }

    // ─── Mappers ──────────────────────────────────────────────────────────

    private ActionEndpoint toEntity(ActionEndpointDto dto, WorkflowConfig existing) {
        String encryptedAuth = resolveEncryptedAuth(dto, existing);

        List<ActionParam> params = dto.getParams() == null ? new ArrayList<>()
                : dto.getParams().stream().map(this::toParamEntity).collect(Collectors.toList());

        return ActionEndpoint.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .triggerPhrases(dto.getTriggerPhrases())
                .url(dto.getUrl())
                .method(dto.getMethod() != null ? dto.getMethod().toUpperCase() : "POST")
                .authType(dto.getAuthType() != null ? dto.getAuthType().toLowerCase() : "none")
                .authValue(encryptedAuth)
                .apiKeyHeader(dto.getApiKeyHeader())
                .params(params)
                .bodyTemplate(dto.getBodyTemplate())
                .responseMode(dto.getResponseMode() != null ? dto.getResponseMode() : "static")
                .responsePath(dto.getResponsePath())
                .successMessage(dto.getSuccessMessage())
                .failureMessage(dto.getFailureMessage())
                .enabled(dto.isEnabled())
                .build();
    }

    private ActionParam toParamEntity(ActionParamDto dto) {
        List<SubParam> subParams = dto.getProperties() == null ? new ArrayList<>()
                : dto.getProperties().stream().map(this::toSubParamEntity).collect(Collectors.toList());
        return ActionParam.builder()
                .id(dto.getId())
                .name(dto.getName())
                .type(dto.getType())
                .description(dto.getDescription())
                .required(dto.isRequired())
                .example(dto.getExample())
                .properties(subParams)
                .build();
    }

    private SubParam toSubParamEntity(SubParamDto dto) {
        return SubParam.builder()
                .id(dto.getId())
                .name(dto.getName())
                .type(dto.getType())
                .description(dto.getDescription())
                .required(dto.isRequired())
                .example(dto.getExample())
                .build();
    }

    /**
     * Encrypts authValue from request.
     * - Blank → preserve existing encrypted value
     * - Already encrypted (starts with "ENCRYPTED:") → store as-is
     * - Plaintext → encrypt with AES-256-GCM
     */
    private String resolveEncryptedAuth(ActionEndpointDto dto, WorkflowConfig existing) {
        String raw = dto.getAuthValue();
        if (raw == null || raw.isBlank() || raw.equals("••••••")) {
            // Preserve existing
            if (existing == null) return null;
            return existing.getActions().stream()
                    .filter(a -> dto.getId() != null && dto.getId().equals(a.getId()))
                    .findFirst()
                    .map(ActionEndpoint::getAuthValue)
                    .orElse(null);
        }
        if (raw.startsWith("ENCRYPTED:")) return raw;
        try {
            return encryptionUtils.encrypt(raw);
        } catch (Exception e) {
            log.error("Failed to encrypt authValue for action {}: {}", dto.getName(), e.getMessage());
            throw new RuntimeException("Failed to encrypt credentials for action: " + dto.getName(), e);
        }
    }

    private WorkflowConfigResponse toResponse(WorkflowConfig config) {
        List<ActionEndpointDto> actions = config.getActions().stream()
                .map(a -> ActionEndpointDto.builder()
                        .id(a.getId())
                        .name(a.getName())
                        .description(a.getDescription())
                        .triggerPhrases(a.getTriggerPhrases())
                        .url(a.getUrl())
                        .method(a.getMethod())
                        .authType(a.getAuthType())
                        .authValue(getAuthValueForResponse(a.getAuthValue()))
                        .apiKeyHeader(a.getApiKeyHeader())
                        .params(a.getParams().stream().map(this::toParamDto).collect(Collectors.toList()))
                        .bodyTemplate(a.getBodyTemplate())
                        .responseMode(a.getResponseMode())
                        .responsePath(a.getResponsePath())
                        .successMessage(a.getSuccessMessage())
                        .failureMessage(a.getFailureMessage())
                        .enabled(a.isEnabled())
                        .build())
                .collect(Collectors.toList());

        return WorkflowConfigResponse.builder()
                .chatbotId(config.getChatbotId())
                .actions(actions)
                .savedAt(config.getUpdatedAt())
                .build();
    }

    private ActionParamDto toParamDto(ActionParam p) {
        List<SubParamDto> subs = p.getProperties() == null ? new ArrayList<>()
                : p.getProperties().stream().map(sp -> SubParamDto.builder()
                        .id(sp.getId()).name(sp.getName()).type(sp.getType())
                        .description(sp.getDescription()).required(sp.isRequired())
                        .example(sp.getExample()).build())
                .collect(Collectors.toList());
        return ActionParamDto.builder()
                .id(p.getId()).name(p.getName()).type(p.getType())
                .description(p.getDescription()).required(p.isRequired())
                .example(p.getExample()).properties(subs).build();
    }

    /**
     * Keep non-secret template expressions visible in UI (e.g. {{userToken}})
     * while masking actual credentials.
     */
    private String getAuthValueForResponse(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) return null;
        try {
            String decrypted = encryptionUtils.decrypt(encryptedValue);
            if ("{{userToken}}".equals(decrypted)) {
                return decrypted;
            }
            return "••••••";
        } catch (Exception e) {
            log.warn("Could not decrypt authValue for response masking: {}", e.getMessage());
            return "••••••";
        }
    }
}
