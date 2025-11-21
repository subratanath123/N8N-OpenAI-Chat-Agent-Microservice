package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "chatBotTask")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotTask {

    @Id
    private String id;
    private String chatbotId;
    private List<ChatBot.QAPair> qaPairs;
    private List<String> fileIds;
    private List<String> addedWebsites;
    private List<String> addedTexts;

}
