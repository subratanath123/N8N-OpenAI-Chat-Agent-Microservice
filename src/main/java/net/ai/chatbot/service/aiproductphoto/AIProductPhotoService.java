package net.ai.chatbot.service.aiproductphoto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.AIProductPhotoJobDao;
import net.ai.chatbot.dao.MediaAssetDao;
import net.ai.chatbot.entity.AIProductPhotoJob;
import net.ai.chatbot.entity.MediaAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIProductPhotoService {

    private final AIProductPhotoJobDao jobDao;
    private final MediaAssetDao assetDao;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/images/generations";

    public String createJob(String userEmail, MultipartFile imageFile, String backgroundType, String customPrompt) {
        log.info("Creating AI Product Photo job for user: {}, backgroundType: {}", userEmail, backgroundType);

        String jobId = UUID.randomUUID().toString();
        AIProductPhotoJob job = AIProductPhotoJob.builder()
                .id(jobId)
                .userEmail(userEmail)
                .backgroundType(backgroundType)
                .customPrompt(customPrompt)
                .status("pending")
                .createdAt(Instant.now())
                .build();

        jobDao.save(job);
        log.info("Job created with ID: {}", jobId);

        generatePhotoAsync(jobId, userEmail, imageFile, backgroundType, customPrompt);

        return jobId;
    }

    @Async
    public void generatePhotoAsync(String jobId, String userEmail, MultipartFile imageFile, 
                                   String backgroundType, String customPrompt) {
        log.info("Starting async product photo generation for job: {}", jobId);

        AIProductPhotoJob job = jobDao.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job not found: {}", jobId);
            return;
        }

        try {
            job.setStatus("processing");
            jobDao.save(job);

            String resultUrl = callOpenAIProductPhoto(imageFile, backgroundType, customPrompt);

            String assetId = saveToAssets(userEmail, resultUrl, backgroundType);

            job.setStatus("completed");
            job.setResultUrl(resultUrl);
            job.setAssetId(assetId);
            job.setCompletedAt(Instant.now());
            jobDao.save(job);

            log.info("Job {} completed successfully", jobId);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus("failed");
            job.setError(e.getMessage());
            jobDao.save(job);
        }
    }

    private String callOpenAIProductPhoto(MultipartFile imageFile, String backgroundType, String customPrompt) throws Exception {
        log.info("Generating product photo with background: {}", backgroundType);

        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new Exception("OpenAI API key not configured");
        }

        String prompt = buildBackgroundPrompt(backgroundType, customPrompt);
        log.info("Generated prompt: {}", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "dall-e-3");
        requestBody.put("prompt", prompt);
        requestBody.put("n", 1);
        requestBody.put("size", "1024x1024");
        requestBody.put("quality", "hd");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    OPENAI_API_URL,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new Exception("Empty response from OpenAI");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            if (data == null || data.isEmpty()) {
                throw new Exception("No image data in response");
            }

            String imageUrl = (String) data.get(0).get("url");
            if (imageUrl == null || imageUrl.isEmpty()) {
                throw new Exception("Invalid image URL from OpenAI");
            }

            log.info("Product photo generated successfully");
            return imageUrl;

        } catch (Exception e) {
            log.error("OpenAI product photo generation failed: {}", e.getMessage(), e);
            throw new Exception("Generation failed: " + e.getMessage());
        }
    }

    private String buildBackgroundPrompt(String backgroundType, String customPrompt) {
        switch (backgroundType) {
            case "white":
                return "Professional product photography on pure white background, studio lighting, high resolution, commercial quality";
            case "gradient":
                return "Product photography with modern gradient background, soft colors, studio lighting, professional commercial style";
            case "lifestyle":
                return "Product in lifestyle setting, realistic environment, natural lighting, professional photography";
            case "custom":
                return customPrompt != null && !customPrompt.isEmpty() ? 
                    "Product photography with " + customPrompt + ", professional lighting, high quality" : 
                    "Professional product photography";
            default:
                return "Professional product photography";
        }
    }

    private String saveToAssets(String userEmail, String imageUrl, String backgroundType) throws Exception {
        log.info("Saving product photo to assets for user: {}", userEmail);

        try {
            MediaAsset asset = MediaAsset.builder()
                    .userEmail(userEmail)
                    .fileName("product-photo-" + System.currentTimeMillis() + ".png")
                    .fileType("image/png")
                    .fileSize(0L)
                    .supabaseUrl(imageUrl)
                    .folderPath("")
                    .source("AI Product Photo - " + backgroundType)
                    .createdAt(Instant.now())
                    .build();

            asset = assetDao.save(asset);
            log.info("Asset saved with ID: {}", asset.getId());
            return asset.getId();

        } catch (Exception e) {
            log.error("Failed to save asset: {}", e.getMessage(), e);
            throw new Exception("Failed to save to assets: " + e.getMessage());
        }
    }

    public AIProductPhotoJob getJobStatus(String jobId, String userEmail) {
        return jobDao.findByIdAndUserEmail(jobId, userEmail).orElse(null);
    }

    public List<AIProductPhotoJob> listJobs(String userEmail) {
        return jobDao.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }
}
