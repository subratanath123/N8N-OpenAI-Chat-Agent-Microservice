package net.ai.chatbot.service.openai;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.ChatMessage;
import net.ai.chatbot.dto.ChatRequest;
import net.ai.chatbot.dto.ChatResponse;
import net.ai.chatbot.dto.Message;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
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

    public ChatResponse chat(String prompt, List<Document> knowledgeBaseResults, List<ChatMessage> recentChatHistory) {
        ChatResponse chatResponse = null;
        List<Message> chatMessages = new ArrayList<>();

        ChatRequest.ChatRequestBuilder request = ChatRequest.builder();

        try {
            // Step 1: Add system role with initial instruction
            chatMessages.add(new Message("system", "You are a helpful AI assistant. Use the chat history and the knowledge base to provide an informative and context-aware answer."));

            List<ChatMessage> reversedHistory = new ArrayList<>(recentChatHistory);
            Collections.reverse(reversedHistory);

            // Step 2: Add recent chat history (reverse chronological to chronological)
            for (ChatMessage msg : reversedHistory) {
                String role = msg.getSenderEmail().startsWith("chatbot") ? "assistant" : "user";
                chatMessages.add(new Message(role, msg.getContent()));
            }

            // Step 3: Add the current user prompt
            chatMessages.add(new Message("user", prompt));

            // Step 4: Add knowledge base snippets as system messages
            for (Document doc : knowledgeBaseResults) {
                chatMessages.add(new Message("system", "Knowledge Base: ".concat(doc.getText())));
            }

            // Step 5: Build and send the request
            request
                    .model(model)
                    .messages(chatMessages)
                    .n(maxCompletions)
                    .temperature(temperature);

            chatResponse = restTemplate.postForObject("https://api.openai.com/v1/chat/completions", request.build(), ChatResponse.class);

        } catch (Exception e) {
            log.error("error : " + e.getMessage());
        }

        return chatResponse;
    }


}
