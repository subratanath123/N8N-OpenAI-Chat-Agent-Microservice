package net.ai.chatbot.dto.social;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FacebookConnectRequest {

    @NotBlank
    private String longLivedToken;

    @Valid
    private List<FacebookPageDto> pages;

    private Long expiresIn;

    @Data
    public static class FacebookPageDto {
        @NotBlank
        private String pageId;
        @NotBlank
        private String pageName;
        @NotBlank
        private String pageAccessToken;
    }
}
