package net.ai.chatbot.controller.file;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.FileUpload;
import net.ai.chatbot.service.aichatbot.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

import static net.ai.chatbot.utils.AuthUtils.getEmail;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/file")
public class FileUploadController {


    @Autowired
    private FileUploadService fileUploadService;

    /*
        TODO:: Don't use the method now...Have to fix file attachment in response.
     */
    @GetMapping("/{id}")
    public FileUpload getFile(@PathVariable(value = "id") String fileId) {
        return fileUploadService.load(fileId);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> handleFormSubmission(@RequestParam(value = "file", required = false) MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .build();
        }

        try {
            FileUpload fileUpload = fileUploadService.save(FileUpload.builder()
                    .data(file.getBytes())
                    .email(getEmail())
                    .contentType(file.getContentType())
                    .fileName(file.getName())
                    .build());

            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileUpload.getId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing file");
        }
    }
}
