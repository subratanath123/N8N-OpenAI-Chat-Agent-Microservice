package net.ai.chatbot.controller;


import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/openai")
public class DocumentTrainController {

    private final VectorStore vectorStore;

    public DocumentTrainController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping("/train")
    public ResponseEntity<String> handleFormSubmission(
            @RequestParam(value = "webSite", required = false) String webSite,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "file", required = false) MultipartFile file) {

//        if (file.isEmpty()) {
//            return ResponseEntity.badRequest().body("File is required");
//        }

        try {

            if (file != null) {
                byte[] fileBytes = file.getBytes();
                String fileName = file.getOriginalFilename();
                System.out.println("Uploaded file: " + fileName + " (size: " + fileBytes.length + " bytes)");
            }

            System.out.println("Website: " + webSite);
            System.out.println("Description: " + description);

            List<Document> documents = List.of(
                    new Document(description)
            );

            vectorStore.add(documents);

            return ResponseEntity.ok("Form submitted successfully!");

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error processing file");
        }
    }

}
