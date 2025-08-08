package net.ai.chatbot.service.n8n;

import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;

import java.util.List;
import java.util.Map;

public interface GenericN8NService<T, R> {

    /**
     * Send a single message to N8N workflow
     * @param message The message to send
     * @param workflowId The N8N workflow ID
     * @param webhookUrl The webhook URL
     * @return N8N response
     */
    N8NChatResponse<R> sendMessage(T message, String workflowId, String webhookUrl);

    /**
     * Send multiple messages to N8N workflow
     * @param messages List of messages to send
     * @param workflowId The N8N workflow ID
     * @param webhookUrl The webhook URL
     * @return N8N response
     */
    N8NChatResponse<R> sendMessages(List<T> messages, String workflowId, String webhookUrl);

    /**
     * Send custom input with additional parameters
     * @param input Custom N8N input with additional parameters
     * @return N8N response
     */
    N8NChatResponse<R> sendCustomInput(N8NChatInput<T> input);

    /**
     * Send message with session context
     * @param message The message to send
     * @param sessionId Session identifier
     * @param workflowId The N8N workflow ID
     * @param webhookUrl The webhook URL
     * @return N8N response
     */
    N8NChatResponse<R> sendMessageWithSession(T message, String sessionId, String workflowId, String webhookUrl);

    /**
     * Send message with additional parameters
     * @param message The message to send
     * @param additionalParams Additional parameters for the workflow
     * @param workflowId The N8N workflow ID
     * @param webhookUrl The webhook URL
     * @return N8N response
     */
    N8NChatResponse<R> sendMessageWithParams(T message, Map<String, Object> additionalParams, String workflowId, String webhookUrl);
}
