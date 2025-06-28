package net.ai.chatbot.entity;

public record ScrappedData(String url,
                           String title,
                           String scrappedData,
                           String html) {
}
