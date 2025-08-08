package net.ai.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatInput {

    private String sessionId;
    private String model;
    private List<Message> messages;
    private Message message;
    private int n;
    private double temperature;

}

