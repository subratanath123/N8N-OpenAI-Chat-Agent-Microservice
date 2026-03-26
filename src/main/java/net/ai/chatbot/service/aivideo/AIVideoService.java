package net.ai.chatbot.service.aivideo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.AIVideoJobDao;
import net.ai.chatbot.dao.MediaAssetDao;
import net.ai.chatbot.entity.AIVideoJob;
import net.ai.chatbot.entity.MediaAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIVideoService {

    private final AIVideoJobDao jobDao;
    private final MediaAssetDao assetDao;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${runway.api.key:}")
    private String runwayApiKey;

    @Value("${stability.api.key:}")
    private String stabilityApiKey;

    private static final String RUNWAY_API_URL = "https://api.runwayml.com/v1/generate";

    /**
     * Create a new video generation job
     */
    public AIVideoJob createJob(String userEmail, String prompt, Integer duration, String aspectRatio) {
        AIVideoJob job = AIVideoJob.builder()
                .id(UUID.randomUUID().toString())
                .userEmail(userEmail)
                .prompt(prompt)
                .duration(duration)
                .aspectRatio(aspectRatio)
                .status("pending")
                .createdAt(Instant.now())
                .build();

        jobDao.save(job);
        log.info("Created video generation job {} for user {}", job.getId(), userEmail);

        // Start async generation
        generateVideoAsync(job.getId());

        return job;
    }

    /**
     * Generate video asynchronously
     */
    @Async
    public void generateVideoAsync(String jobId) {
        AIVideoJob job = jobDao.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job {} not found", jobId);
            return;
        }

        try {
            // Update status to processing
            job.setStatus("processing");
            jobDao.save(job);
            log.info("Starting video generation for job {}", jobId);

            // Call video generation API
            String videoUrl = callVideoGenerationAPI(job.getPrompt(), job.getDuration(), job.getAspectRatio());

            // Save video URL to assets
            String assetId = saveToAssets(job.getUserEmail(), videoUrl, job.getPrompt(), job.getDuration());

            // Update job as completed
            job.setStatus("completed");
            job.setVideoUrl(videoUrl);
            job.setAssetId(assetId);
            job.setCompletedAt(Instant.now());
            jobDao.save(job);

            log.info("Completed video generation for job {}", jobId);

        } catch (Exception e) {
            log.error("Failed to generate video for job {}: {}", jobId, e.getMessage(), e);
            job.setStatus("failed");
            job.setError(e.getMessage());
            jobDao.save(job);
        }
    }

    /**
     * Call video generation API (Runway ML, Stability AI, or other providers)
     * 
     * This is a placeholder implementation. You can integrate with:
     * - Runway ML Gen-2/Gen-3
     * - Stability AI Video
     * - Pika Labs
     * - Other video generation APIs
     */
    private String callVideoGenerationAPI(String prompt, Integer duration, String aspectRatio) throws Exception {
        log.info("Calling video generation API - prompt: '{}', duration: {}s, ratio: {}", 
                 prompt.substring(0, Math.min(50, prompt.length())), duration, aspectRatio);

        // Check if API keys are configured
        if ((runwayApiKey == null || runwayApiKey.isEmpty()) && 
            (stabilityApiKey == null || stabilityApiKey.isEmpty())) {
            
            log.warn("No video generation API key configured. Using mock response.");
            
            // For now, return a placeholder/demo video URL
            // Replace this with actual API integration
            throw new Exception("Video generation API not configured. Please add runway.api.key or stability.api.key to application.properties");
        }

        // Example: Runway ML API integration (uncomment and configure when ready)
        /*
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + runwayApiKey);

        Map<String, Object> requestBody = Map.of(
            "prompt", prompt,
            "duration", duration,
            "aspect_ratio", aspectRatio,
            "model", "gen3"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                RUNWAY_API_URL,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (response.getBody() == null) {
                throw new Exception("Empty response from Runway API");
            }

            String videoUrl = (String) response.getBody().get("video_url");
            if (videoUrl == null || videoUrl.isEmpty()) {
                throw new Exception("Invalid video URL from Runway API");
            }

            log.info("Successfully generated video, URL: {}", videoUrl.substring(0, Math.min(80, videoUrl.length())));
            return videoUrl;

        } catch (Exception e) {
            log.error("Runway API call failed: {}", e.getMessage());
            throw new Exception("Video generation failed: " + e.getMessage());
        }
        */

        // Placeholder - replace with actual API call
        throw new Exception("Video generation API integration pending. Please configure API keys.");
    }

    /**
     * Save video URL and metadata to MongoDB
     */
    private String saveToAssets(String userEmail, String videoUrl, String prompt, Integer duration) throws Exception {
        try {
            log.info("Saving AI generated video URL to database for user {}", userEmail);

            // Create filename from prompt
            String filename = prompt.substring(0, Math.min(50, prompt.length()))
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase() + "-" + System.currentTimeMillis() + ".mp4";

            // Save metadata to database
            MediaAsset asset = MediaAsset.builder()
                    .id(UUID.randomUUID().toString())
                    .userEmail(userEmail)
                    .fileName(filename)
                    .mimeType("video/mp4")
                    .sizeBytes(0L) // Size unknown
                    .supabaseUrl(videoUrl) // Store video URL here
                    .objectPath("ai-generated-videos/" + userEmail + "/" + filename)
                    .createdAt(Instant.now())
                    .tags(new ArrayList<>(List.of("ai-generated", "ai-video", "duration-" + duration + "s")))
                    .folderPath("AI Generated Videos")
                    .build();

            assetDao.save(asset);
            log.info("Saved AI generated video metadata as asset {} for user {}", asset.getId(), userEmail);

            return asset.getId();

        } catch (Exception e) {
            log.error("Failed to save video metadata: {}", e.getMessage(), e);
            throw new Exception("Failed to save video metadata: " + e.getMessage());
        }
    }

    /**
     * Get job status
     */
    public AIVideoJob getJobStatus(String userEmail, String jobId) {
        return jobDao.findByIdAndUserEmail(jobId, userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    /**
     * List all jobs for user
     */
    public List<AIVideoJob> listJobs(String userEmail) {
        return jobDao.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }
}
