package net.ai.chatbot.dto.aichatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public response for widget embedding
 * Returns chatbot configuration without sensitive data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicChatbotResponseDto {
    
    private String id;
    private String name;
    private String title;
    private String greetingMessage;
    private Integer width;
    private Integer height;
    private String status;  // "ACTIVE" | "DISABLED" for widget behavior
    
    // Theme fields (flat structure for widget compatibility)
    private String headerBackground;
    private String headerText;
    private String aiBackground;
    private String aiText;
    private String userBackground;
    private String userText;
    private String widgetPosition;
    private String aiAvatar;          // Full URL for preset avatars (http/https)
    private String avatarFileId;      // File ID for custom uploaded avatars
    private Boolean hideMainBannerLogo;
    
    // Optionally include nested theme object for new widgets
    private ChatbotWidgetThemeDto theme;
}
