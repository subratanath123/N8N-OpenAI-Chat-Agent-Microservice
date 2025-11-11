package net.ai.chatbot.dto.n8n;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.Message;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8NChatInput {

    private String chatbotId;
    private String sessionId;
    private String model;
    private Message message;
    private List<Attachment> attachments;
    private Map<String, Object> additionalParams;
    private String webhookUrl;

    // Generic constructor for simple message
    public static N8NChatInput of(Message message, String webhookUrl) {
        return N8NChatInput.builder()
                .message(message)
                .chatbotId(message.getChatbotId())
                .sessionId(message.getSessionId())
                .webhookUrl(webhookUrl)
                .build();
    }

}
