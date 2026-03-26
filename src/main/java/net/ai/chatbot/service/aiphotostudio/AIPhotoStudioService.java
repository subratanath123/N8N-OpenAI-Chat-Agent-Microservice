package net.ai.chatbot.service.aiphotostudio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.AIPhotoStudioJobDao;
import net.ai.chatbot.dao.MediaAssetDao;
import net.ai.chatbot.entity.AIPhotoStudioJob;
import net.ai.chatbot.entity.MediaAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIPhotoStudioService {
    private static final long OPENAI_IMAGE_MAX_BYTES = 4L * 1024 * 1024;

    private final AIPhotoStudioJobDao jobDao;
    private final MediaAssetDao assetDao;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    private static final String OPENAI_EDIT_API_URL = "https://api.openai.com/v1/images/edits";

    public String createJob(String userEmail, MultipartFile imageFile, String instruction, String editType) {
        log.info("Creating AI Photo Studio job for user: {}", userEmail);

        String jobId = UUID.randomUUID().toString();
        AIPhotoStudioJob job = AIPhotoStudioJob.builder()
                .id(jobId)
                .userEmail(userEmail)
                .instruction(instruction)
                .editType(editType)
                .status("pending")
                .createdAt(Instant.now())
                .build();

        jobDao.save(job);
        log.info("Job created with ID: {}", jobId);

        editImageAsync(jobId, userEmail, imageFile, instruction, editType);

        return jobId;
    }

    @Async
    public void editImageAsync(String jobId, String userEmail, MultipartFile imageFile, String instruction, String editType) {
        log.info("Starting async image edit for job: {}", jobId);

        AIPhotoStudioJob job = jobDao.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job not found: {}", jobId);
            return;
        }

        try {
            job.setStatus("processing");
            jobDao.save(job);

            String resultUrl = callOpenAIEdit(imageFile, instruction, editType);

            String assetId = saveToAssets(userEmail, resultUrl, editType);

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

    private String callOpenAIEdit(MultipartFile imageFile, String instruction, String editType) throws Exception {
        log.info("Calling OpenAI Image Edit API - editType: {}, originalSize: {} bytes", 
                 editType, imageFile.getSize());

        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new Exception("OpenAI API key not configured");
        }

        String prompt = buildPrompt(instruction, editType);
        log.info("Generated prompt: {}", prompt);

        try {
            byte[] preparedImageBytes = prepareImageForOpenAI(imageFile);
            BufferedImage preparedImage = ImageIO.read(new ByteArrayInputStream(preparedImageBytes));
            if (preparedImage == null) {
                throw new Exception("Failed to prepare image for OpenAI edits.");
            }
            byte[] maskBytes = createTransparentMaskPng(preparedImage.getWidth(), preparedImage.getHeight());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            ByteArrayResource imageResource = new ByteArrayResource(preparedImageBytes) {
                @Override
                public String getFilename() {
                    return "image.png";
                }
            };
            HttpHeaders imagePartHeaders = new HttpHeaders();
            imagePartHeaders.setContentType(MediaType.IMAGE_PNG);
            HttpEntity<ByteArrayResource> imagePart = new HttpEntity<>(imageResource, imagePartHeaders);
            body.add("image", imagePart);

            ByteArrayResource maskResource = new ByteArrayResource(maskBytes) {
                @Override
                public String getFilename() {
                    return "mask.png";
                }
            };
            HttpHeaders maskPartHeaders = new HttpHeaders();
            maskPartHeaders.setContentType(MediaType.IMAGE_PNG);
            HttpEntity<ByteArrayResource> maskPart = new HttpEntity<>(maskResource, maskPartHeaders);
            body.add("mask", maskPart);

            body.add("model", "dall-e-2");
            body.add("prompt", prompt);
            body.add("n", "1");
            body.add("size", "1024x1024");

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("Sending edit request to OpenAI...");
            ResponseEntity<Map> response = restTemplate.exchange(
                    OPENAI_EDIT_API_URL,
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
                throw new Exception("No image data in OpenAI response");
            }

            String imageUrl = (String) data.get(0).get("url");
            if (imageUrl == null || imageUrl.isEmpty()) {
                throw new Exception("Invalid image URL from OpenAI");
            }

            log.info("OpenAI edit successful, URL: {}", imageUrl.substring(0, Math.min(80, imageUrl.length())));
            return imageUrl;

        } catch (Exception e) {
            log.error("OpenAI edit failed: {}", e.getMessage(), e);
            throw new Exception("OpenAI edit failed: " + e.getMessage());
        }
    }

    /**
     * OpenAI image edits require PNG uploads smaller than 4MB.
     * This normalizes any uploaded image to a square PNG and downsizes if needed.
     */
    private byte[] prepareImageForOpenAI(MultipartFile imageFile) throws Exception {
        BufferedImage input = ImageIO.read(new ByteArrayInputStream(imageFile.getBytes()));
        if (input == null) {
            throw new Exception("Unsupported image format. Please upload a valid image file.");
        }

        BufferedImage square = toSquare(input);
        int targetSize = Math.min(1024, Math.min(square.getWidth(), square.getHeight()));

        while (targetSize >= 256) {
            BufferedImage resized = resize(square, targetSize, targetSize);
            byte[] pngBytes = toPngBytes(resized);
            if (pngBytes.length < OPENAI_IMAGE_MAX_BYTES) {
                return pngBytes;
            }
            targetSize /= 2;
        }

        throw new Exception("Image is too large for OpenAI edits even after optimization. Please use a simpler image.");
    }

    private BufferedImage toSquare(BufferedImage src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;

        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, side, side, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return square;
    }

    private BufferedImage resize(BufferedImage src, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    private byte[] toPngBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(image, "png", baos);
        if (!written) {
            throw new Exception("Failed to encode image as PNG.");
        }
        return baos.toByteArray();
    }

    private byte[] createTransparentMaskPng(int width, int height) throws Exception {
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        return toPngBytes(mask);
    }

    private String buildPrompt(String instruction, String editType) {
        String forceChange = " ";
        switch (editType) {
            case "enhance":
                return "Enhance this image quality, improve colors, sharpness, and details." + forceChange;
            case "remove-bg":
                return "Remove the background and keep only the main subject on transparent background." + forceChange;
            case "recolor":
                return "Apply professional AI recoloring, enhance vibrancy and color grading with a clearly different look." + forceChange;
            default:
                if (instruction != null && !instruction.isEmpty()) {
                    return instruction + forceChange;
                }
                return "";
        }
    }

    private String saveToAssets(String userEmail, String imageUrl, String editType) throws Exception {
        log.info("Saving edited image to assets for user: {}", userEmail);

        try {
            MediaAsset asset = MediaAsset.builder()
                    .userEmail(userEmail)
                    .fileName("ai-photo-edit-" + System.currentTimeMillis() + ".png")
                    .fileType("image/png")
                    .fileSize(0L)
                    .supabaseUrl(imageUrl)
                    .folderPath("")
                    .source("AI Photo Studio - " + editType)
                    .uploadedAt(Instant.now())
                    .build();

            asset = assetDao.save(asset);
            log.info("Asset saved with ID: {}", asset.getId());
            return asset.getId();

        } catch (Exception e) {
            log.error("Failed to save asset: {}", e.getMessage(), e);
            throw new Exception("Failed to save to assets: " + e.getMessage());
        }
    }

    public AIPhotoStudioJob getJobStatus(String jobId, String userEmail) {
        return jobDao.findByIdAndUserEmail(jobId, userEmail).orElse(null);
    }

    public List<AIPhotoStudioJob> listJobs(String userEmail) {
        return jobDao.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }
}
