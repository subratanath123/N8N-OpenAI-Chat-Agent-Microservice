package net.ai.chatbot.dto;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "projects")
public class Project {

    @Id
    private String id;

    private String projectName;
    private String websiteToTrain;
    private String chatBotName;
    private String chatBotImageUrl;
    private String description;
    private String embedWebsiteUrl;
    private String createdBy;
    private Date createdAt;
    @Transient
    private MultipartFile file;
}
