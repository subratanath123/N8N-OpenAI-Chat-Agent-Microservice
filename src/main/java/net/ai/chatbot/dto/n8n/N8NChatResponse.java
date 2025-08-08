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
public class N8NChatResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<T> choices;
    private Map<String, Object> metadata;
    private String workflowId;
    private long timestamp;
    private String errorCode;
    private String errorMessage;
    
    // N8N specific fields
    private String output;
    private Map<String, Object> body;
    private Object result;
    private String status;
    private Map<String, Object> headers;

    // Generic success response
    public static <T> N8NChatResponse<T> success(T data) {
        return N8NChatResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // Generic success response with choices
    public static <T> N8NChatResponse<T> success(List<T> choices) {
        return N8NChatResponse.<T>builder()
                .success(true)
                .choices(choices)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // Generic error response
    public static <T> N8NChatResponse<T> error(String errorMessage) {
        return N8NChatResponse.<T>builder()
                .success(false)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // Generic error response with error code
    public static <T> N8NChatResponse<T> error(String errorCode, String errorMessage) {
        return N8NChatResponse.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // Builder method for metadata
    public N8NChatResponse<T> withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    // Builder method for workflow ID
    public N8NChatResponse<T> withWorkflowId(String workflowId) {
        this.workflowId = workflowId;
        return this;
    }
    
    // Helper method to get the actual response content
    public String getResponseContent() {
        if (output != null) {
            return output;
        }
        if (message != null) {
            return message;
        }
        if (data != null) {
            return data.toString();
        }
        if (result != null) {
            return result.toString();
        }
        return null;
    }
}
