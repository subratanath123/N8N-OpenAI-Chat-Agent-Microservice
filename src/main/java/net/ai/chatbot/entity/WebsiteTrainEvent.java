package net.ai.chatbot.entity;

import java.io.Serializable;

public record WebsiteTrainEvent(
        String projectId,
        String email,
        String projectName,
        String websiteUrl,
        String baseUrl
) implements Serializable {


}