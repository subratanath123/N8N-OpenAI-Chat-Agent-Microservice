package net.ai.chatbot.controller.aiphotostudio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.AIPhotoStudioJob;
import net.ai.chatbot.service.aiphotostudio.AIPhotoStudioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/ai-photo-studio")
@RequiredArgsConstructor
@Slf4j
public class AIPhotoStudioController {

    private final AIPhotoStudioService photoStudioService;

    @PostMapping("/edit")
    public ResponseEntity<?> editImage(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(value = "instruction", required = false, defaultValue = "") String instruction,
            @RequestParam("editType") String editType,
            Authentication authentication
    ) {
        try {
            String userEmail = ((Jwt) authentication.getPrincipal()).getClaim("email");
            log.info("Edit image request from user: {}, editType: {}", userEmail, editType);

            if (imageFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
            }

            String jobId = photoStudioService.createJob(userEmail, imageFile, instruction, editType);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "pending");
            response.put("message", "Image edit started");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Edit image failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getJobStatus(
            @PathVariable String jobId,
            Authentication authentication
    ) {
        try {
            String userEmail = ((Jwt) authentication.getPrincipal()).getClaim("email");

            AIPhotoStudioJob job = photoStudioService.getJobStatus(jobId, userEmail);

            if (job == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("id", job.getId());
            response.put("status", job.getStatus());
            response.put("resultUrl", job.getResultUrl());
            response.put("assetId", job.getAssetId());
            response.put("error", job.getError());
            response.put("createdAt", job.getCreatedAt());
            response.put("completedAt", job.getCompletedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Get job status failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> listJobs(Authentication authentication) {
        try {
            String userEmail = ((Jwt) authentication.getPrincipal()).getClaim("email");

            List<AIPhotoStudioJob> jobs = photoStudioService.listJobs(userEmail);

            return ResponseEntity.ok(jobs);

        } catch (Exception e) {
            log.error("List jobs failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
