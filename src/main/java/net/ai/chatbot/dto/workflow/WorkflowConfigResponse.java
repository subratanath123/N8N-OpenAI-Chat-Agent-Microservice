package net.ai.chatbot.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowConfigResponse {
    private String chatbotId;
    private List<ActionEndpointDto> actions;
    private Instant savedAt;
}
