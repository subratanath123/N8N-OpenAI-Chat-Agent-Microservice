package net.ai.chatbot.service.n8n;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.FileAttachment;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.webclient.GenericWebClient;
import net.ai.chatbot.service.webclient.GenericWebClientResponse;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GenericN8NService<T, R> {

    private final GenericWebClient genericWebClient;

    @Autowired
    public GenericN8NService(GenericWebClient genericWebClient) {
        this.genericWebClient = genericWebClient;
    }

    public N8NChatResponse<R> sendMessage(ChatBot chatBot, Message message, String webhookUrl) {
        if (message == null) {
            return N8NChatResponse.<R>error("INVALID_MESSAGE", "Message payload is required");
        }

        return executeWebhook(
                chatBot,
                webhookUrl,
                message.getChatbotId(),
                message.getMessage(),
                Optional.ofNullable(message.getSessionId()).orElse(null),
                chatBot.getChatbotknowledgebasecollection(),
                chatBot.getVectorIndexName(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                message.getFileAttachments()
        );
    }

    private N8NChatResponse<R> executeWebhook(ChatBot chatBot,
                                              String webhookUrl,
                                              String chatbotId,
                                              String messageContent,
                                              String conversationId,
                                              String chatbotCollection,
                                              String vectorIndexName,
                                              Map<String, Object> extraFormFields,
                                              Map<String, String> extraHeaders, List<FileAttachment> fileAttachments) {

        if (webhookUrl == null || webhookUrl.isBlank()) {
            return N8NChatResponse.error("INVALID_URL", "Webhook URL is required");
        }

        if (messageContent == null || messageContent.isBlank()) {
            return N8NChatResponse.error("INVALID_MESSAGE", "Message content is required");
        }

        try {
            Map<String, String> headers = buildHeaders(chatBot, chatbotCollection, chatbotId,
                    vectorIndexName, conversationId, extraHeaders);

            // Add multimodal headers
            if (fileAttachments != null && !fileAttachments.isEmpty()) {
                headers.put("multimodal-type", "vector-references");
                headers.put("file-count", String.valueOf(fileAttachments.size()));
                headers.put("file-ids", fileAttachments.stream().map(FileAttachment::getFileId).collect(Collectors.joining(",")));
                headers.put("fileType", fileAttachments.stream().map(FileAttachment::getMimeType).collect(Collectors.joining(",")));
            }

            // Send as form data (text message only)
            GenericWebClientResponse<String> responseEntity = genericWebClient.postWithResponse(
                    webhookUrl,
                    () -> BodyInserters.fromFormData(buildFormDataAsStringMap(chatBot, messageContent, extraFormFields)),
                    String.class,
                    headers
            );

            return buildChatResponse(responseEntity);

        } catch (RuntimeException e) {
            log.error("HTTP error calling N8N webhook {}: {}", webhookUrl, e.getMessage());
            return N8NChatResponse.<R>error("HTTP_ERROR", "HTTP error calling N8N webhook: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling N8N webhook {}: {}", webhookUrl, e.getMessage());
            return N8NChatResponse.<R>error("UNEXPECTED_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

    private Map<String, String> buildHeaders(ChatBot chatBot,
                                             String chatbotCollection,
                                             String chatbotId,
                                             String vectorIndexName,
                                             String conversationId,
                                             Map<String, String> extraHeaders) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        headers.put("email", AuthUtils.getEmail());

        if (chatBot != null) {
            headers.put("fallbackmessage", chatBot.getFallbackMessage());
            headers.put("greetingmessage", chatBot.getGreetingMessage());
            headers.put("restrictdatasource", String.valueOf(chatBot.getRestrictToDataSource()));
        }

        headers.put("chatbotid", chatbotId);
        headers.put("chatbotknowledgebasecollection", chatbotCollection);
        headers.put("vectorindexname", vectorIndexName);
        headers.put("authenticated", vectorIndexName);

        if (conversationId != null && !conversationId.isBlank()) {
            headers.put("sessionid", conversationId);
            headers.put("conversationid", conversationId);
        }

        if (extraHeaders != null) {
            extraHeaders.forEach((key, value) -> {
                if (key != null && value != null) {
                    headers.put(key, value);
                }
            });
        }

        return headers;
    }

    private MultiValueMap<String, String> buildFormDataAsStringMap(ChatBot chatBot, String messageContent,
                                                                   Map<String, Object> extraFormFields) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("message", messageContent);
        formData.add("instructions", chatBot.getInstructions());

        if (extraFormFields != null && !extraFormFields.isEmpty()) {
            extraFormFields.forEach((key, value) -> {
                if (key != null && value != null) {
                    formData.add(key, value.toString());
                }
            });
        }

        return formData;
    }

    private N8NChatResponse<R> buildChatResponse(GenericWebClientResponse<String> responseEntity) {
        N8NChatResponse<R> response = new N8NChatResponse<>();
        response.setTimestamp(System.currentTimeMillis());
        response.setStatus(responseEntity.getStatusCode() != null ? responseEntity.getStatusCode().toString() : null);
        response.setSuccess(responseEntity.is2xxSuccessful());
        response.setResult(responseEntity.getBody());

        Map<String, Object> headerMap = Optional.ofNullable(responseEntity.getHeaders())
                .map(headers -> headers.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().size() == 1 ? entry.getValue().get(0) : entry.getValue()
                        )))
                .orElse(Collections.emptyMap());

        response.setHeaders(headerMap);

        return response;
    }
}
