package net.ai.chatbot.service.aiimage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.AIImageJobDao;
import net.ai.chatbot.dao.MediaAssetDao;
import net.ai.chatbot.entity.AIImageJob;
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

import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIImageService {

    private final AIImageJobDao jobDao;
    private final MediaAssetDao assetDao;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/images/generations";

    /**
     * Create a new image generation job
     */
    public AIImageJob createJob(String userEmail, String prompt, String size, String quality, String style) {
        AIImageJob job = AIImageJob.builder()
                .id(UUID.randomUUID().toString())
                .userEmail(userEmail)
                .prompt(prompt)
                .size(size)
                .quality(quality)
                .style(style)
                .status("pending")
                .createdAt(Instant.now())
                .build();

        jobDao.save(job);
        log.info("Created image generation job {} for user {}", job.getId(), userEmail);

        // Start async generation
        generateImageAsync(job.getId());

        return job;
    }

    /**
     * Generate image asynchronously
     */
    @Async
    public void generateImageAsync(String jobId) {
        AIImageJob job = jobDao.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job {} not found", jobId);
            return;
        }

        try {
            // Update status to processing
            job.setStatus("processing");
            jobDao.save(job);
            log.info("Starting image generation for job {}", jobId);

            // Call OpenAI API
            String imageUrl = callOpenAI(job.getPrompt(), job.getSize(), job.getQuality(), job.getStyle());

            // Save image URL to assets (just metadata, no file download)
            String assetId = saveToAssets(job.getUserEmail(), imageUrl, job.getPrompt());

            // Update job as completed
            job.setStatus("completed");
            job.setImageUrl(imageUrl);
            job.setAssetId(assetId);
            job.setCompletedAt(Instant.now());
            jobDao.save(job);

            log.info("Completed image generation for job {}", jobId);

        } catch (Exception e) {
            log.error("Failed to generate image for job {}: {}", jobId, e.getMessage(), e);
            job.setStatus("failed");
            job.setError(e.getMessage());
            jobDao.save(job);
        }
    }

    /**
     * Call OpenAI DALL-E API
     */
    private String callOpenAI(String prompt, String size, String quality, String style) throws Exception {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        log.info("Calling OpenAI API - prompt: '{}', size: {}, quality: {}, style: {}", 
                 prompt.substring(0, Math.min(50, prompt.length())), size, quality, style);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openaiApiKey);

        Map<String, Object> requestBody = Map.of(
            "model", "dall-e-3",
            "prompt", prompt,
            "n", 1,
            "size", size,
            "quality", quality,
            "style", style
        );

        log.debug("OpenAI request body: {}", requestBody);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                OPENAI_API_URL,
                HttpMethod.POST,
                request,
                Map.class
            );

            log.info("OpenAI response status: {}", response.getStatusCode());

            if (response.getBody() == null) {
                throw new Exception("Empty response from OpenAI");
            }

            log.debug("OpenAI response body: {}", response.getBody());

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            if (data == null || data.isEmpty()) {
                throw new Exception("No image returned from OpenAI");
            }

            String imageUrl = (String) data.get(0).get("url");
            if (imageUrl == null || imageUrl.isEmpty()) {
                throw new Exception("Invalid image URL from OpenAI");
            }

            log.info("Successfully generated image, URL: {}", imageUrl.substring(0, Math.min(80, imageUrl.length())));
            return imageUrl;

        } catch (Exception e) {
            log.error("OpenAI API call failed: {} - {}", e.getClass().getName(), e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("421")) {
                throw new Exception("OpenAI API endpoint error. Please verify API key and endpoint configuration.");
            }
            throw new Exception("OpenAI generation failed: " + e.getMessage());
        }
    }

    /**
     * Save image URL and metadata to MongoDB (no file download/upload)
     */
    private String saveToAssets(String userEmail, String imageUrl, String prompt) throws Exception {
        try {
            log.info("Saving AI generated image URL to database for user {}", userEmail);

            // Create filename from prompt
            String filename = prompt.substring(0, Math.min(50, prompt.length()))
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase() + "-" + System.currentTimeMillis() + ".png";

            // Save metadata to database with OpenAI URL
            MediaAsset asset = MediaAsset.builder()
                    .id(UUID.randomUUID().toString())
                    .userEmail(userEmail)
                    .fileName(filename)
                    .mimeType("image/png")
                    .sizeBytes(0L) // Size unknown (not downloaded)
                    .supabaseUrl(imageUrl) // Store OpenAI URL here
                    .objectPath("ai-generated/" + userEmail + "/" + filename)
                    .createdAt(Instant.now())
                    .tags(new ArrayList<>(List.of("ai-generated", "dall-e")))
                    .folderPath("AI Generated")
                    .build();

            assetDao.save(asset);
            log.info("Saved AI generated image metadata as asset {} for user {}", asset.getId(), userEmail);

            return asset.getId();

        } catch (Exception e) {
            log.error("Failed to save image metadata: {}", e.getMessage(), e);
            throw new Exception("Failed to save image metadata: " + e.getMessage());
        }
    }

    /**
     * Get job status
     */
    public AIImageJob getJobStatus(String userEmail, String jobId) {
        return jobDao.findByIdAndUserEmail(jobId, userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    /**
     * List all jobs for user
     */
    public List<AIImageJob> listJobs(String userEmail) {
        return jobDao.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }
}
