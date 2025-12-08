package net.ai.chatbot.entity;

import lombok.Builder;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Builder
@Document(collection = "messengerIntegration")
public record MessengerIntegration(
        @Id
        String id,
        String chatbotId,
        String pageName,
        String pageId,
        String accessToken,
        String verifyToken,
        Boolean enabled
) {
}