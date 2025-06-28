package net.ai.chatbot.entity;

import java.io.Serializable;

public record WebsiteTrainEvent(
        String email,
        String websiteUrl,
        String baseUrl
) implements Serializable {


}