package net.ai.chatbot.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubParamDto {
    private String id;
    private String name;
    private String type;
    private String description;
    private boolean required;
    private String example;
}
