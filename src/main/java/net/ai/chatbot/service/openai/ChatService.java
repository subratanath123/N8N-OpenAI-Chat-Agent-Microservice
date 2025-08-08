package net.ai.chatbot.service.openai;

import net.ai.chatbot.dto.ChatMessage;
import net.ai.chatbot.dto.ChatResponse;
import org.springframework.ai.document.Document;

import java.util.List;

public interface ChatService {

    ChatResponse chat(String message, String sessionId);
    ChatResponse chat(String prompt, List<Document> knowledgeBaseResults, List<ChatMessage> recentChatHistory);

}
