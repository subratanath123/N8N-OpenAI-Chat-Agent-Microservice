package net.ai.chatbot.entity;

import lombok.Builder;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Builder
@Document(collection = "whatsappIntegration")
public record WhatsAppIntegration(
        @Id
        String id,
        String name,
        String businessAccountId,
        String chatbotId,
        String phoneNumberId,
        String phoneNumber,
        String appSecret,
        String appId,
        String webhookVerifyToken,
        String accessToken,
        Boolean enabled
) {
}

