package net.ai.chatbot.dto;

import lombok.*;
import net.ai.chatbot.enums.MessageType;
import org.springframework.data.annotation.Transient;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {

    private String content;
    private String senderEmail;
    private String attachmentLink;
    private Date created;
    private String role; // Added for frontend compatibility (user, assistant, system)

    @Transient
    private String token;

    @Transient
    private MessageType type;
}