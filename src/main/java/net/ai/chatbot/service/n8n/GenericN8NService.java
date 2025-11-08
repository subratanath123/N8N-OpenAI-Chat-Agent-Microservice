package net.ai.chatbot.service.n8n;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GenericN8NService<T, R> {

    @Autowired
    private RestTemplate n8nRestTemplate;

    public N8NChatResponse<R> sendMessage(T message, String workflowId, String webhookUrl) {
        try {
            log.info("Sending message to N8N workflow: {}", workflowId);

            N8NChatInput<T> input = N8NChatInput.<T>of(message)
                    .withWorkflow(webhookUrl);

            return sendCustomInput(input);
        } catch (Exception e) {
            log.error("Error sending message to N8N workflow {}: {}", workflowId, e.getMessage());
            return N8NChatResponse.<R>error("N8N_ERROR", "Failed to send message to N8N workflow: " + e.getMessage());
        }
    }

    public N8NChatResponse<R> sendMessages(List<T> messages, String workflowId, String webhookUrl) {
        try {
            log.info("Sending {} messages to N8N workflow: {}", messages.size(), workflowId);

            N8NChatInput<T> input = N8NChatInput.<T>of(messages)
                    .withWorkflow(webhookUrl);

            return sendCustomInput(input);
        } catch (Exception e) {
            log.error("Error sending messages to N8N workflow {}: {}", workflowId, e.getMessage());
            return N8NChatResponse.<R>error("N8N_ERROR", "Failed to send messages to N8N workflow: " + e.getMessage());
        }
    }

    public N8NChatResponse<R> sendCustomInput(N8NChatInput<T> input) {
        try {
            String webhookUrl = input.getWebhookUrl();
            if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
                return N8NChatResponse.<R>error("INVALID_URL", "Webhook URL is required");
            }

            log.info("Sending custom input to N8N webhook: {}", webhookUrl);

            // Process attachments if present - extract first attachment data
            N8NChatInput<T> processedInput = input;

            // Log the final request body for debugging
            log.info("Final N8N request body: sessionId={}, model={}, message={}, messages={}, attachments={}, additionalParams={}",
                    processedInput.getSessionId(), processedInput.getModel(), processedInput.getMessage(),
                    processedInput.getMessages(), processedInput.getAttachments(), processedInput.getAdditionalParams());

            // Additional logging for message details
            if (processedInput.getMessage() instanceof net.ai.chatbot.dto.Message) {
                net.ai.chatbot.dto.Message message = (net.ai.chatbot.dto.Message) processedInput.getMessage();
                log.info("Message details: role={}, content={}, messageAttachments={}",
                        message.getRole(), message.getContent(), message.getAttachments());
            }

            // Use Object.class to get the raw response first
            Object rawResponse = n8nRestTemplate.postForObject(webhookUrl, processedInput, Object.class);

            if (rawResponse == null) {
                return N8NChatResponse.<R>error("NULL_RESPONSE", "Received null response from N8N");
            }

            log.info("Raw N8N response: {}", rawResponse);

            // Create a response object and populate it based on the raw response
            N8NChatResponse<R> response = new N8NChatResponse<>();
            response.setSuccess(true);
            response.setTimestamp(System.currentTimeMillis());

            // Handle different response formats
            if (rawResponse instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) rawResponse;

                // Check for common N8N response fields
                if (responseMap.containsKey("output")) {
                    response.setOutput((String) responseMap.get("output"));
                }
                if (responseMap.containsKey("body")) {
                    response.setBody((Map<String, Object>) responseMap.get("body"));
                }
                if (responseMap.containsKey("result")) {
                    response.setResult(responseMap.get("result"));
                }
                if (responseMap.containsKey("status")) {
                    response.setStatus((String) responseMap.get("status"));
                }
                if (responseMap.containsKey("headers")) {
                    response.setHeaders((Map<String, Object>) responseMap.get("headers"));
                }
                if (responseMap.containsKey("message")) {
                    response.setMessage((String) responseMap.get("message"));
                }
                if (responseMap.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    R data = (R) responseMap.get("data");
                    response.setData(data);
                }
            } else if (rawResponse instanceof String) {
                // If it's just a string, treat it as output
                response.setOutput((String) rawResponse);
            } else {
                // For other types, try to convert to string
                response.setOutput(rawResponse.toString());
            }

            return response;

        } catch (RestClientException e) {
            log.error("HTTP error calling N8N webhook: {}", e.getMessage());
            return N8NChatResponse.<R>error("HTTP_ERROR", "HTTP error calling N8N webhook: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling N8N webhook: {}", e.getMessage());
            return N8NChatResponse.<R>error("UNEXPECTED_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

    public N8NChatResponse<R> sendMessageWithSession(T message, String sessionId, String workflowId, String webhookUrl) {
        try {
            log.info("Sending message with session {} to N8N workflow: {}", sessionId, workflowId);

            N8NChatInput<T> input = N8NChatInput.<T>of(message)
                    .withWorkflow(webhookUrl);

            input.setSessionId(sessionId);

            return sendCustomInput(input);
        } catch (Exception e) {
            log.error("Error sending message with session to N8N workflow {}: {}", workflowId, e.getMessage());
            return N8NChatResponse.<R>error("N8N_ERROR", "Failed to send message with session: " + e.getMessage());
        }
    }

    /**
     * Helper method to create a list with a single message
     */
    private List<T> createSingleMessageList(T message) {
        List<T> list = new ArrayList<>();
        list.add(message);
        return list;
    }

}
