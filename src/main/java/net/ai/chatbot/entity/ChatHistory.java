package net.ai.chatbot.entity;

import lombok.*;
import net.ai.chatbot.dto.ChatMessage;
import net.ai.chatbot.dto.User;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document
public class ChatHistory {

    @Id
    private String id;

    private String projectId;
    private List<User> users;
    private List<ChatMessage> messages;

}
