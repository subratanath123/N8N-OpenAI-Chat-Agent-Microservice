package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatDao;
import net.ai.chatbot.dto.ChatMessage;
import net.ai.chatbot.entity.ChatHistory;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api")
public class UserChatController {

    @Autowired
    private ChatDao chatDao;

    @RequestMapping("/get-recent-messages/{userEmail}")
    public List<ChatMessage> sendToOtherUser(@PathVariable String userEmail) {
        log.info("get recent messages with {}", userEmail);

        String chatHistoryId = chatDao.getChatHistoryId(userEmail, AuthUtils.getEmail());

        List<ChatMessage> chatMessageList = chatDao.getChatHistory(chatHistoryId).getMessages();

        return chatMessageList != null
                ? chatMessageList
                : new ArrayList<>();
    }

    @RequestMapping("/get-recent-messages/{userEmail}/{projectId}")
    public List<ChatMessage> sendToOtherUser(@PathVariable String userEmail,
                                             @PathVariable String projectId) {

        log.info("get recent messages with {}", userEmail);

        ChatHistory chatHistory = chatDao.getChatHistoryWithMessages(userEmail, AuthUtils.getEmail(), projectId);

        return chatHistory != null
                ? chatHistory.getMessages()
                : new ArrayList<>();
    }

}
