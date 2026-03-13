package net.ai.chatbot.dto.social;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwitterConnectRequest {

    @NotBlank
    private String accessToken;

    private String refreshToken;

    private Long expiresIn;

    @NotBlank
    private String username;
}
