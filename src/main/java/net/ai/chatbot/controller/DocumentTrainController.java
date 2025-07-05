package net.ai.chatbot.controller;


import lombok.AllArgsConstructor;
import net.ai.chatbot.service.training.ChatBotTrainingService;
import org.apache.tika.exception.TikaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/openai")
@AllArgsConstructor
public class DocumentTrainController {

    private final ChatBotTrainingService chatBotTrainingService;

    @PostMapping("/train")
    public ResponseEntity<String> handleFormSubmission(
            @RequestParam(value = "webSite", required = false) String webSite,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        try {

//            initiateTrainingChatBot(webSite, description, file);

            return ResponseEntity.ok("Form submitted successfully!");

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing file");
        }
    }

    private void initiateTrainingChatBot(String webSite, String description, MultipartFile file) throws IOException, TikaException {
//        if (webSite != null && webSite.length() > 0) {
//            chatBotTrainingService.handleWebsiteUrlTraining(webSite, "project");
//
//        } else if (description != null && description.length() > 0) {
//            chatBotTrainingService.handleTextBasedTraining(description, "project");
//
//        } else if (file != null && !file.isEmpty()) {
//            chatBotTrainingService.handleFileTraining(file, "project");
//        }
    }
}
