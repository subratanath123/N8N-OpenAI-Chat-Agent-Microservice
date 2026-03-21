package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chatbot_workflow_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowConfig {

    @Id
    private String id;

    @Indexed(unique = true)
    private String chatbotId;

    /** Clerk user email — chatbot owner */
    private String ownerId;

    @Builder.Default
    private List<ActionEndpoint> actions = new ArrayList<>();

    private Instant updatedAt;

    // ─────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionEndpoint {
        /** Frontend-generated unique ID */
        private String id;
        private String name;
        private String description;
        /** Comma-separated trigger phrases for fast-path keyword matching */
        private String triggerPhrases;
        private String url;
        /** GET | POST | PUT | PATCH | DELETE */
        private String method;
        /** none | bearer | apikey | basic */
        private String authType;
        /** AES-256-GCM encrypted at rest */
        private String authValue;
        /** Header name used only when authType = "apikey" */
        private String apiKeyHeader;
        @Builder.Default
        private List<ActionParam> params = new ArrayList<>();
        private String bodyTemplate;
        private String successMessage;
        private String failureMessage;
        private boolean enabled;
    }

    // ─────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionParam {
        private String id;
        private String name;
        /** string | number | boolean | array | object */
        private String type;
        private String description;
        private boolean required;
        private String example;
        /** Populated when type = "object" */
        @Builder.Default
        private List<SubParam> properties = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubParam {
        private String id;
        private String name;
        /** string | number | boolean | array */
        private String type;
        private String description;
        private boolean required;
        private String example;
    }
}
