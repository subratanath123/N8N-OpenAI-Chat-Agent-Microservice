package net.ai.chatbot.dto.aichatbot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.validator.ChatBotValidator.ValidChatBotName;
import net.ai.chatbot.validator.ChatBotValidator.ValidFileList;
import net.ai.chatbot.validator.ChatBotValidator.ValidQAPairs;
import net.ai.chatbot.validator.ChatBotValidator.ValidTextList;
import net.ai.chatbot.validator.ChatBotValidator.ValidUrlList;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotCreationRequest {
    
    @Size(max = 100, message = "Title must not exceed 100 characters")
    private String title;
    
    @NotBlank(message = "Chatbot name is required")
    @ValidChatBotName
    private String name;
    
    private Boolean hideName;
    
    @Size(max = 5000, message = "Instructions must not exceed 5000 characters")
    private String instructions;
    
    private Boolean restrictToDataSource;
    private Boolean customFallbackMessage;
    
    @Size(max = 1000, message = "Fallback message must not exceed 1000 characters")
    private String fallbackMessage;
    
    @Size(max = 1000, message = "Greeting message must not exceed 1000 characters")
    private String greetingMessage;
    
    private String selectedDataSource;
    
    // Q&A pairs
    @Valid
    @ValidQAPairs
    private List<ChatBot.QAPair> qaPairs;
    
    // Uploaded files (file names or IDs)
    @ValidFileList
    private List<String> fileIds;
    
    // Website URLs to scrape
    @ValidUrlList
    private List<String> addedWebsites;
    
    // Plain text content
    @ValidTextList
    private List<String> addedTexts;

}

