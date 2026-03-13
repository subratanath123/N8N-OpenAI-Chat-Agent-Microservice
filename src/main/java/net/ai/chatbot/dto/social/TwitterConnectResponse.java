package net.ai.chatbot.dto.social;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TwitterConnectResponse {
    private boolean success;
    private String accountId;
    private String platform;
}
