package net.ai.chatbot.service.openai;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.ChatRequest;
import net.ai.chatbot.dto.ChatResponse;
import net.ai.chatbot.dto.Message;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OpenAiService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-completions}")
    private int maxCompletions;

    @Value("${openai.temperature}")
    private double temperature;

    @Value("${openai.api.url}")
    private String apiUrl;

    public ChatResponse chat(String prompt, List<Document> knowledgeBaseResults) {
        /* Initialize variables */
        ChatResponse chatResponse = null;
        List<Message> chatMessages = new ArrayList<>();

        ChatRequest.ChatRequestBuilder request = ChatRequest.builder();
        try {
            /* Add user prompt to chat messages */
            chatMessages.add(new Message("user", prompt));
            chatMessages.add(new Message("system", "You are a helpful AI assistant. Use the following knowledge base to answer the query."));

            // Add knowledge base results to chat messages
            for (Document doc : knowledgeBaseResults) {
                chatMessages.add(new Message("system", "Knowledge Base:".concat(doc.getText())));
            }

            /* Build chat request */
            request
                    .model(model)
                    .messages(chatMessages)
                    .n(maxCompletions)
                    .temperature(temperature);

            /* Send chat request and obtain response */
            chatResponse = restTemplate.postForObject("https://api.openai.com/v1/chat/completions", request.build(), ChatResponse.class);

        } catch (Exception e) {
            log.error("error : " + e.getMessage());
        }

        return chatResponse;
    }

}
