package net.ai.chatbot.service.n8n;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.service.webclient.GenericWebClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class N8nWebhookService {

    private final GenericWebClient webClient;

    public N8nWebhookService(GenericWebClient webClient) {
        this.webClient = webClient;
    }

    public void submitAttachmentToN8nKnowledgebase(byte[] fileBytes, String fileName, String email,
                                                   String collectionName, String vectorIndexName,
                                                   String dataType, String webhookUrl) {
        try {
            log.info("Sending attachment to n8n webhook: {}", webhookUrl);

            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            Map<String, String> headers = new HashMap<>();
            headers.put("dataType", dataType);
            headers.put("collectionName", collectionName);
            headers.put("vectorIndexName", vectorIndexName);
            headers.put("email", email);

            String response = webClient.post(webhookUrl,
                    () -> BodyInserters.fromMultipartData("body", fileResource),
                    String.class,
                    headers);

            log.info("File '{}' successfully sent to n8n. Response: {}", fileName, response);

        } catch (Exception e) {
            log.error("Error sending file to n8n webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send attachment to n8n", e);
        }
    }

    public void submitTextContentToN8nKnowledgebase(String semanticText,  String email,
                                                    String collectionName, String vectorIndexName,
                                                    String dataType, String webhookUrl) {
        try {
            log.info("Sending attachment to n8n webhook: {}", webhookUrl);

            Map<String, Object> requestBody = Map.of("body", semanticText);

            Map<String, String> headers = new HashMap<>();
            headers.put("dataType", dataType);
            headers.put("collectionName", collectionName);
            headers.put("vectorIndexName", vectorIndexName);
            headers.put("email", email);

            String response = webClient.post(webhookUrl,
                    () -> requestBody,
                    String.class,
                    headers);

            log.info("Text content for '{}' successfully sent to n8n. Response: {}", email, response);

        } catch (Exception e) {
            log.error("Error sending file to n8n webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send attachment to n8n", e);
        }
    }
}
