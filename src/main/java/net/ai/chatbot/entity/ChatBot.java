package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document(collection = "chatbots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBot {
    
    @Id
    private String id;
    
    private String title;
    private String name;
    private Boolean hideName;
    private String instructions;
    private Boolean restrictToDataSource;
    private Boolean customFallbackMessage;
    private String fallbackMessage;
    private String greetingMessage;
    private String selectedDataSource;
    
    // Q&A pairs
    private List<QAPair> qaPairs;
    
    // Uploaded files (file names or IDs)
    private List<String> uploadedFiles;
    
    // Website URLs to scrape
    private List<String> addedWebsites;
    
    // Plain text content
    private List<String> addedTexts;
    
    // Metadata
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;
    private String status; // CREATED, TRAINING, COMPLETED, FAILED
    
    // Inner class for Q&A pairs
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QAPair {
        private String question;
        private String answer;
    }
}

