package net.ai.chatbot.dto;

import lombok.*;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "projectTrainingInfos")
public class ProjectTrainingInfo {

    private String projectName;
    private String projectId;
    private String email;
    private String childPageUrl;
    private Date created;
}
