package net.ai.chatbot.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionEndpointDto {
    private String id;
    private String name;
    private String description;
    /** Comma-separated trigger phrases */
    private String triggerPhrases;
    private String url;
    private String method;
    private String authType;
    /** Plaintext on save request; masked as "••••••" on GET response */
    private String authValue;
    private String apiKeyHeader;
    @Builder.Default
    private List<ActionParamDto> params = new ArrayList<>();
    private String bodyTemplate;
    /** "static" | "dynamic" — defaults to static */
    private String responseMode;
    /** Dot-path into upstream response, e.g. "message" or "data.reply" */
    private String responsePath;
    private String successMessage;
    private String failureMessage;
    private boolean enabled;
}
