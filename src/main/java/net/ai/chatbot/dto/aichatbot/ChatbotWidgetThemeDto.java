package net.ai.chatbot.dto.aichatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Widget theme configuration for chatbot embed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotWidgetThemeDto {
    
    /** Hex color, e.g. #2D3748 */
    private String headerBackground;
    
    private String headerText;
    
    private String aiBackground;
    
    private String aiText;
    
    private String userBackground;
    
    private String userText;
    
    /** "left" | "right" */
    private String widgetPosition;
    
    /** Public URL for avatar image */
    private String aiAvatar;
    
    private Boolean hideMainBannerLogo;
}
