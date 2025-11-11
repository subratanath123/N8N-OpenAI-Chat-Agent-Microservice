package net.ai.chatbot.service.n8n;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GenericN8NService<T, R> {

    @Autowired
    private RestTemplate n8nRestTemplate;

    public N8NChatResponse<R> sendMessage(Message message, String webhookUrl) {
        if (message == null) {
            return N8NChatResponse.<R>error("INVALID_MESSAGE", "Message payload is required");
        }

        return executeWebhook(
                webhookUrl,
                message.getMessage(),
                Optional.ofNullable(message.getSessionId()).orElse(null),
                "jade-ai-knowledgebase-" + Optional.ofNullable(message.getChatbotId()).orElse(null),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    public N8NChatResponse<R> sendMessages(List<Message> messages, String webhookUrl) {
        if (messages == null || messages.isEmpty()) {
            return N8NChatResponse.<R>error("INVALID_MESSAGES", "At least one message is required");
        }

        List<N8NChatResponse<R>> responses = new ArrayList<>();
        for (Message message : messages) {
            N8NChatResponse<R> response = sendMessage(message, webhookUrl);
            responses.add(response);

            if (!response.isSuccess()) {
                return response;
            }
        }

        List<R> aggregatedResults = responses.stream()
                .map(N8NChatResponse::getData)
                .collect(Collectors.toList());

        N8NChatResponse<R> aggregatedResponse = N8NChatResponse.success(aggregatedResults);
        aggregatedResponse.setMessage("Messages processed successfully");
        aggregatedResponse.setResult(aggregatedResults);
        aggregatedResponse.setTimestamp(System.currentTimeMillis());
        return aggregatedResponse;
    }

    public N8NChatResponse<R> sendCustomInput(N8NChatInput input) {
        if (input == null) {
            return N8NChatResponse.<R>error("INVALID_INPUT", "Input payload is required");
        }

        Message message = input.getMessage();
        if (message == null || message.getMessage() == null || message.getMessage().isBlank()) {
            return N8NChatResponse.<R>error("INVALID_MESSAGE", "Message content is required");
        }

        Map<String, Object> additionalParams = Optional.ofNullable(input.getAdditionalParams())
                .orElse(Collections.emptyMap());

        return executeWebhook(
                input.getWebhookUrl(),
                message.getMessage(),
                Optional.ofNullable(message.getSessionId()).orElse(input.getSessionId()),
                "jade-ai-knowledgebase-" + Optional.ofNullable(message.getChatbotId()).orElse(input.getChatbotId()),
                additionalParams,
                Collections.emptyMap()
        );
    }

    public N8NChatResponse<R> sendMessageWithSession(Message message, String sessionId, String webhookUrl) {
        try {
            log.info("Sending message with session {} ", sessionId);

            if (message == null) {
                return N8NChatResponse.<R>error("INVALID_MESSAGE", "Message payload is required");
            }

            return executeWebhook(
                    webhookUrl,
                    message.getMessage(),
                    sessionId,
                    "jade-ai-knowledgebase-" + message.getChatbotId(),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );
        } catch (Exception e) {
            log.error("Error sending message with session to N8N: {}", e.getMessage());
            return N8NChatResponse.<R>error("N8N_ERROR", "Failed to send message with session: " + e.getMessage());
        }
    }

    private N8NChatResponse<R> executeWebhook(String webhookUrl,
                                              String messageContent,
                                              String conversationId,
                                              String chatbotCollection,
                                              Map<String, Object> extraFormFields,
                                              Map<String, String> extraHeaders) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return N8NChatResponse.<R>error("INVALID_URL", "Webhook URL is required");
        }

        if (messageContent == null || messageContent.isBlank()) {
            return N8NChatResponse.<R>error("INVALID_MESSAGE", "Message content is required");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.ALL));

            if (conversationId != null && !conversationId.isBlank()) {
                headers.add("conversationid", conversationId);
            }

            if (chatbotCollection != null && !chatbotCollection.isBlank()) {
                headers.add("chatbotKnowledgebaseCollection", chatbotCollection);
            }

            if (extraHeaders != null) {
                extraHeaders.forEach((key, value) -> {
                    if (key != null && value != null) {
                        headers.add(key, value);
                    }
                });
            }

            MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
            formData.add("message", messageContent);

            if (extraFormFields != null && !extraFormFields.isEmpty()) {
                extraFormFields.forEach((key, value) -> {
                    if (key != null && value != null) {
                        formData.add(key, value);
                    }
                });
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(formData, headers);

            ResponseEntity<String> responseEntity = n8nRestTemplate.postForEntity(webhookUrl, requestEntity, String.class);

            N8NChatResponse<R> response = new N8NChatResponse<>();
            response.setTimestamp(System.currentTimeMillis());
            response.setStatus(responseEntity.getStatusCode().toString());
            response.setSuccess(responseEntity.getStatusCode().is2xxSuccessful());
            response.setOutput(responseEntity.getBody());
            response.setResult(responseEntity.getBody());
            response.setMessage(responseEntity.getBody());

            @SuppressWarnings("unchecked")
            R data = (R) responseEntity.getBody();
            response.setData(data);

            Map<String, Object> headerMap = responseEntity.getHeaders().toSingleValueMap().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            response.setHeaders(headerMap);

            return response;

        } catch (RestClientException e) {
            log.error("HTTP error calling N8N webhook {}: {}", webhookUrl, e.getMessage());
            return N8NChatResponse.<R>error("HTTP_ERROR", "HTTP error calling N8N webhook: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling N8N webhook {}: {}", webhookUrl, e.getMessage());
            return N8NChatResponse.<R>error("UNEXPECTED_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

}
