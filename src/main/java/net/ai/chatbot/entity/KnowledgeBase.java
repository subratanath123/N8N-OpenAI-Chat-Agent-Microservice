package net.ai.chatbot.entity;

import lombok.*;
import net.ai.chatbot.enums.KnowledgeBaseType;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@Document
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeBase {

    @Id
    private String id;
    private String chatbotId;
    private String knowledgeOf;
    private KnowledgeBaseType knowledgeType;

    @CreatedBy
    private String createdBy;

    @CreatedDate
    private Instant created;

}
