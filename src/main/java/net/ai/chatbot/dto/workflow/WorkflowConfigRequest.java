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
public class WorkflowConfigRequest {
    @Builder.Default
    private List<ActionEndpointDto> actions = new ArrayList<>();
}
