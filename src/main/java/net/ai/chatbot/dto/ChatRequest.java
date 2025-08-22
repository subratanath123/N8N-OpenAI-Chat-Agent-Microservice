package net.ai.chatbot.dto;

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
public class ChatRequest {
    private String message;
    private List<Attachment> attachments;
    private String sessionId;
    private String webhookUrl;
    private String workflowId;
    private Map<String, Object> additionalParams;
}
