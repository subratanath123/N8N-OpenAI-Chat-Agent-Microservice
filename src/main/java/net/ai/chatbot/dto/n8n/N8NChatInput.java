package net.ai.chatbot.dto.n8n;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8NChatInput<T> {

    private String sessionId;
    private String model;
    private T message;
    private List<T> messages;
    private int n;
    private double temperature;
    private Map<String, Object> additionalParams;
    private String workflowId;
    private String webhookUrl;

    // Generic constructor for simple message
    public static <T> N8NChatInput<T> of(T message) {
        return N8NChatInput.<T>builder()
                .message(message)
                .build();
    }

    // Generic constructor for message list
    public static <T> N8NChatInput<T> of(List<T> messages) {
        return N8NChatInput.<T>builder()
                .messages(messages)
                .build();
    }

    // Builder method for workflow configuration
    public N8NChatInput<T> withWorkflow(String workflowId, String webhookUrl) {
        this.workflowId = workflowId;
        this.webhookUrl = webhookUrl;
        return this;
    }

    // Builder method for additional parameters
    public N8NChatInput<T> withAdditionalParams(Map<String, Object> additionalParams) {
        this.additionalParams = additionalParams;
        return this;
    }
}
