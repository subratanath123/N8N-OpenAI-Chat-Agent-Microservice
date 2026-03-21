package net.ai.chatbot.controller.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.workflow.WorkflowConfigRequest;
import net.ai.chatbot.dto.workflow.WorkflowConfigResponse;
import net.ai.chatbot.service.workflow.WorkflowConfigService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/api/chatbot/{chatbotId}/workflow")
@RequiredArgsConstructor
@Slf4j
public class WorkflowConfigController {

    private final WorkflowConfigService workflowConfigService;

    /** GET /v1/api/chatbot/{chatbotId}/workflow — authValue masked as ●●●●●● */
    @GetMapping
    public ResponseEntity<?> getWorkflow(@PathVariable String chatbotId) {
        try {
            return ResponseEntity.ok(workflowConfigService.getWorkflow(chatbotId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching workflow for {}: {}", chatbotId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /v1/api/chatbot/{chatbotId}/workflow
     * Creates or replaces workflow config.
     * Send authValue as plaintext — backend encrypts before storing.
     * Omit / send "••••••" to preserve existing credentials.
     */
    @PostMapping
    public ResponseEntity<?> saveWorkflow(@PathVariable String chatbotId,
                                          @RequestBody WorkflowConfigRequest request) {
        try {
            String ownerId = AuthUtils.getUserEmail();
            WorkflowConfigResponse response = workflowConfigService.saveWorkflow(chatbotId, ownerId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error saving workflow for {}: {}", chatbotId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
