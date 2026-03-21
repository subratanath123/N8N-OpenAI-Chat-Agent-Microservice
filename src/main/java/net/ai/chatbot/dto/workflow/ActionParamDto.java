package net.ai.chatbot.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionParamDto {
    private String id;
    private String name;
    private String type;
    private String description;
    private boolean required;
    private String example;
    @Builder.Default
    private List<SubParamDto> properties = new ArrayList<>();
}
