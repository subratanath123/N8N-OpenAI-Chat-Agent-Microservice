package net.ai.chatbot.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatDao;
import net.ai.chatbot.dao.ProjectDao;
import net.ai.chatbot.dto.ChatMessage;
import net.ai.chatbot.dto.Project;
import net.ai.chatbot.service.openai.OpenAiService;
import net.ai.chatbot.service.pinnecone.PineconeVectorStoreFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static net.ai.chatbot.utils.VectorDatabaseUtils.getNameSpace;

@Controller
@Slf4j
@AllArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ChatDao chatDao;
    private final ProjectDao projectDao;

    private final OpenAiService openAiService;

    private final PineconeVectorStoreFactory pineconeVectorStoreFactory;


    @MessageMapping("/chat.register")
    @SendTo("/topic/public")
    public ChatMessage register(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        chatMessage.setCreated(new Date());

        return chatMessage;
    }

    @MessageMapping("/chat.send")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setCreated(new Date());

        return chatMessage;
    }

    @MessageMapping("/user-message-{userEmail}-{projectId}")
    public void sendToOtherUser(@Payload ChatMessage chatMessage,
                                @DestinationVariable String userEmail,
                                @DestinationVariable String projectId,
                                @Header("simpSessionId") String sessionId) {

        chatMessage.setCreated(new Date());

        log.info("sending message: source: {}", sessionId);

        String chatHistoryId = chatDao.getChatHistoryId(userEmail, chatMessage.getSenderEmail());
        chatDao.saveChatHistory(chatHistoryId, chatMessage);

        chatDao.getChatHistory(userEmail, chatMessage.getSenderEmail());

        //Sending self message to self chatbox
        simpMessagingTemplate.convertAndSend("/queue/reply-" + chatMessage.getSenderEmail(), chatMessage);

        //Sending other users/chabots message to self chatbox
        if (userEmail.equals("chatbot")) {
            Project project = projectDao.findById(projectId);

            VectorStore knowledgeBaseVectorStore = pineconeVectorStoreFactory.createForNamespace(getNameSpace(chatMessage.getSenderEmail(), project.getProjectName()));

            List<ChatMessage> lastChatMessages = chatDao.getLastChatMessages(userEmail, chatMessage.getSenderEmail(), 0, 10).getContent();

            String previousChatContents = lastChatMessages.stream().map(chat -> chat.getSenderEmail().concat(":").concat(chat.getContent()))
                    .collect(Collectors.joining(","));

            List<Document> knowledgeBaseResults = knowledgeBaseVectorStore
                    .similaritySearch(SearchRequest.builder()
                            .query(previousChatContents.concat(", user question: ").concat(chatMessage.getContent()))
                            .topK(10)
                            .build());

            ChatMessage botReplayMessage = ChatMessage
                    .builder()
                    .content(openAiService.chat(chatMessage.getContent(), knowledgeBaseResults, lastChatMessages).getChoices().get(0).getMessage().getContent())
                    .senderEmail(userEmail)
                    .created(new Date())
                    .build();

            simpMessagingTemplate.convertAndSend("/queue/reply-" + chatMessage.getSenderEmail(), botReplayMessage
            );

            chatDao.saveChatHistory(chatHistoryId, botReplayMessage);

        } else {
            simpMessagingTemplate.convertAndSend("/queue/reply-" + userEmail, chatMessage);
        }
    }

    private void saveOngoingChat(VectorStore chatHistoryVectorStore, ChatMessage chatMessage, String userEmail, ChatMessage botReplayMessage) {

        // Step 2: Create Documents for both messages
        Document userDoc = new Document(
                chatMessage.getContent(),
                Map.of(
                        "sender", "user",
                        "email", chatMessage.getSenderEmail(),
                        "timestamp", new Date().getTime()
                )
        );

        Document botDoc = new Document(
                botReplayMessage.getContent(),
                Map.of(
                        "sender", "chatBot's Answer for user query",
                        "email", userEmail,
                        "timestamp", new Date().getTime()
                )
        );

        chatHistoryVectorStore.add(Arrays.asList(userDoc, botDoc));
    }

}