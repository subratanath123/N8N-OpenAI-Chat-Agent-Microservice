package net.ai.chatbot.entity;

import java.io.Serializable;

public record WebsiteTrainEvent(
        String id,
        String email,
        String websiteUrl,
        String baseUrl,
        String description
) implements Serializable {


}