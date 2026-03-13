package net.ai.chatbot.dto.social;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenResolutionRequest {

    @NotBlank
    private String targetId;
}
