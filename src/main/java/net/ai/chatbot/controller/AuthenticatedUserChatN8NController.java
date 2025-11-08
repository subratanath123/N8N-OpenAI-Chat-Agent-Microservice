package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.service.n8n.GenericN8NService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/n8n/authenticated")
public class AuthenticatedUserChatN8NController {

    @Autowired
    private GenericN8NService<Message, Object> n8nService;

    /**
     * Get session ID from authentication context or return default
     */
    private String getSessionId() {
        try {
            return AuthUtils.getEmail();
        } catch (Exception e) {
            log.warn("Could not get email from authentication context, using default session ID");
            return "default_session_" + System.currentTimeMillis();
        }
    }

    /**
     * Send a single message to N8N workflow
     */
    @PostMapping("/chat")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(
            @RequestBody Message message,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl) {
        
        log.info("Received chat request for workflow: {}", workflowId);
        
        N8NChatResponse<Object> response = n8nService.sendMessage(message, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Send multiple messages to N8N workflow
     */
    @PostMapping("/chat/batch")
    public ResponseEntity<N8NChatResponse<Object>> sendMessages(
            @RequestBody List<Message> messages,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl) {
        
        log.info("Received batch chat request for workflow: {}", workflowId);
        
        N8NChatResponse<Object> response = n8nService.sendMessages(messages, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Send message with session context
     */
    @PostMapping("/chat/session")
    public ResponseEntity<N8NChatResponse<Object>> sendMessageWithSession(
            @RequestBody Message message,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl,
            @RequestParam(required = false) String sessionId) {
        
        String currentSessionId = sessionId != null ? sessionId : getSessionId();
        log.info("Received session chat request for workflow: {} with session: {}", workflowId, currentSessionId);
        
        N8NChatResponse<Object> response = n8nService.sendMessageWithSession(message, currentSessionId, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

//    /**
//     * Send message with additional parameters
//     */
//    @PostMapping("/chat/params")
//    public ResponseEntity<N8NChatResponse<Object>> sendMessageWithParams(
//            @RequestBody Message message,
//            @RequestParam String workflowId,
//            @RequestParam String webhookUrl,
//            @RequestBody(required = false) Map<String, Object> additionalParams) {
//
//        log.info("Received parameterized chat request for workflow: {}", workflowId);
//
//        N8NChatResponse<Object> response = n8nService.sendMessageWithParams(message, additionalParams, workflowId, webhookUrl);
//
//        return ResponseEntity.ok(response);
//    }

    /**
     * Send custom input with full control over the request
     * Now supports optional attachments - the N8N service will process them automatically
     */
    @PostMapping("/chat/custom")
    public ResponseEntity<N8NChatResponse<Object>> sendCustomInput(
            @RequestBody N8NChatInput<Message> customInput) {
        
        log.info("Received custom chat request for workflow: {}", customInput.getWebhookUrl());
        
        // The N8N service will automatically process attachments if present
        N8NChatResponse<Object> response = n8nService.sendCustomInput(customInput);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Send file upload directly to N8N webhook as multipart form data
     */
    @PostMapping("/chat/file/direct")
    public ResponseEntity<N8NChatResponse<Object>> sendFileDirectlyToN8N(
            @RequestParam("file") MultipartFile file,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String sessionId) {
        
        try {
            log.info("Received direct file upload request for workflow: {}, file: {} ({} bytes, type: {})", 
                    workflowId, file.getOriginalFilename(), file.getSize(), file.getContentType());
            
            // Create multipart form data for N8N webhook
            MultiValueMap<String, Object> multipartData = new LinkedMultiValueMap<>();
            
            // Add the file
            multipartData.add("file", file.getResource());
            
            // Add other parameters
            multipartData.add("workflowId", workflowId);
            multipartData.add("sessionId", sessionId != null ? sessionId : getSessionId());
            if (message != null) {
                multipartData.add("message", message);
            }
            
            // Set headers for multipart form data
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // Create HTTP entity with multipart data
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(multipartData, headers);
            
            // Send directly to N8N webhook
            RestTemplate restTemplate = new RestTemplate();
            Object response = restTemplate.postForObject(webhookUrl, requestEntity, Object.class);
            
            // Create response object
            N8NChatResponse<Object> n8nResponse = new N8NChatResponse<>();
            n8nResponse.setSuccess(true);
            n8nResponse.setTimestamp(System.currentTimeMillis());
            n8nResponse.setResult(response);
            
            log.info("File successfully sent directly to N8N webhook: {}", webhookUrl);
            return ResponseEntity.ok(n8nResponse);
            
        } catch (Exception e) {
            log.error("Error sending file directly to N8N webhook {}: {}", webhookUrl, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(N8NChatResponse.<Object>error("DIRECT_UPLOAD_ERROR", 
                            "Failed to send file directly to N8N: " + e.getMessage()));
        }
    }

    /**
     * Send multiple files directly to N8N webhook as multipart form data
     */
    @PostMapping("/chat/files/direct")
    public ResponseEntity<N8NChatResponse<Object>> sendMultipleFilesDirectlyToN8N(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String sessionId) {
        
        try {
            log.info("Received direct multiple files upload request for workflow: {}, file count: {}", 
                    workflowId, files.size());
            
            // Create multipart form data for N8N webhook
            MultiValueMap<String, Object> multipartData = new LinkedMultiValueMap<>();
            
            // Add all files
            for (MultipartFile file : files) {
                multipartData.add("files", file.getResource());
                log.info("Added file: {} ({} bytes, type: {})", 
                        file.getOriginalFilename(), file.getSize(), file.getContentType());
            }
            
            // Add other parameters
            multipartData.add("workflowId", workflowId);
            multipartData.add("sessionId", sessionId != null ? sessionId : getSessionId());
            if (message != null) {
                multipartData.add("message", message);
            }
            
            // Set headers for multipart form data
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // Create HTTP entity with multipart data
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(multipartData, headers);
            
            // Send directly to N8N webhook
            RestTemplate restTemplate = new RestTemplate();
            Object response = restTemplate.postForObject(webhookUrl, requestEntity, Object.class);
            
            // Create response object
            N8NChatResponse<Object> n8nResponse = new N8NChatResponse<>();
            n8nResponse.setSuccess(true);
            n8nResponse.setTimestamp(System.currentTimeMillis());
            n8nResponse.setResult(response);
            
            log.info("Multiple files successfully sent directly to N8N webhook: {}", webhookUrl);
            return ResponseEntity.ok(n8nResponse);
            
        } catch (Exception e) {
            log.error("Error sending multiple files directly to N8N webhook {}: {}", webhookUrl, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(N8NChatResponse.<Object>error("DIRECT_UPLOAD_ERROR", 
                            "Failed to send files directly to N8N: " + e.getMessage()));
        }
    }

    /**
     * Send file upload to N8N workflow for processing (PDF, documents, etc.)
     * This endpoint converts files to base64 for the existing N8N service
     */
    @PostMapping("/chat/file")
    public ResponseEntity<N8NChatResponse<Object>> sendFileToN8N(
            @RequestParam("file") MultipartFile file,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String sessionId) {
        
        try {
            log.info("Received file upload request for workflow: {}, file: {} ({} bytes, type: {})", 
                    workflowId, file.getOriginalFilename(), file.getSize(), file.getContentType());
            
            // Convert MultipartFile to Attachment DTO
            Attachment attachment = Attachment.builder()
                    .name(file.getOriginalFilename())
                    .size(file.getSize())
                    .type(file.getContentType())
                    .base64(Base64.getEncoder().encodeToString(file.getBytes()))
                    .build();
            
            // Create message with attachment
            Message messageWithAttachment = Message.builder()
                    .role("user")
                    .content(message != null ? message : "Processing uploaded file: " + file.getOriginalFilename())
                    .attachments(List.of(attachment))
                    .build();
            
            // Create N8N input with the message and attachment
            N8NChatInput<Message> n8nInput = N8NChatInput.<Message>builder()
                    .message(messageWithAttachment)
                    .sessionId(sessionId != null ? sessionId : getSessionId())
                    .webhookUrl(webhookUrl)
                    .build();
            
            // Send to N8N service
            N8NChatResponse<Object> response = n8nService.sendCustomInput(n8nInput);
            
            log.info("File successfully sent to N8N workflow: {}", workflowId);
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("Error processing file upload for workflow {}: {}", workflowId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(N8NChatResponse.<Object>error("FILE_PROCESSING_ERROR", 
                            "Failed to process file: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in file upload for workflow {}: {}", workflowId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(N8NChatResponse.<Object>error("INTERNAL_ERROR", 
                            "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Send multiple files to N8N workflow for batch processing
     * This endpoint converts files to base64 for the existing N8N service
     */
    @PostMapping("/chat/files")
    public ResponseEntity<N8NChatResponse<Object>> sendMultipleFilesToN8N(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam String workflowId,
            @RequestParam String webhookUrl,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String sessionId) {
        
        try {
            log.info("Received multiple files upload request for workflow: {}, file count: {}", 
                    workflowId, files.size());
            
            // Convert all MultipartFiles to Attachment DTOs
            List<Attachment> attachments = new ArrayList<>();
            for (MultipartFile file : files) {
                Attachment attachment = Attachment.builder()
                        .name(file.getOriginalFilename())
                        .size(file.getSize())
                        .type(file.getContentType())
                        .base64(Base64.getEncoder().encodeToString(file.getBytes()))
                        .build();
                attachments.add(attachment);
                
                log.info("Processed file: {} ({} bytes, type: {})", 
                        file.getOriginalFilename(), file.getSize(), file.getContentType());
            }
            
            // Create message with all attachments
            Message messageWithAttachments = Message.builder()
                    .role("user")
                    .content(message != null ? message : "Processing " + files.size() + " uploaded files")
                    .attachments(attachments)
                    .build();
            
            // Create N8N input with the message and attachments
            N8NChatInput<Message> n8nInput = N8NChatInput.<Message>builder()
                    .message(messageWithAttachments)
                    .sessionId(sessionId != null ? sessionId : getSessionId())
                    .webhookUrl(webhookUrl)
                    .build();
            
            // Send to N8N service
            N8NChatResponse<Object> response = n8nService.sendCustomInput(n8nInput);
            
            log.info("Multiple files successfully sent to N8N workflow: {}", workflowId);
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("Error processing multiple files upload for workflow {}: {}", workflowId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(N8NChatResponse.<Object>error("FILE_PROCESSING_ERROR", 
                            "Failed to process files: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in multiple files upload for workflow {}: {}", workflowId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(N8NChatResponse.<Object>error("INTERNAL_ERROR", 
                            "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint for N8N workflows
     */
    @GetMapping("/health/{workflowId}")
    public ResponseEntity<N8NChatResponse<Object>> healthCheck(
            @PathVariable String workflowId,
            @RequestParam String webhookUrl) {
        
        log.info("Health check request for workflow: {}", workflowId);
        
        Message healthMessage = Message.builder()
                .role("system")
                .content("health_check")
                .build();
        
        N8NChatResponse<Object> response = n8nService.sendMessage(healthMessage, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get workflow status
     */
    @GetMapping("/status/{workflowId}")
    public ResponseEntity<N8NChatResponse<Object>> getWorkflowStatus(
            @PathVariable String workflowId,
            @RequestParam String webhookUrl) {
        
        log.info("Status check request for workflow: {}", workflowId);
        
        Message statusMessage = Message.builder()
                .role("system")
                .content("status_check")
                .build();
        
        N8NChatResponse<Object> response = n8nService.sendMessage(statusMessage, workflowId, webhookUrl);
        
        return ResponseEntity.ok(response);
    }
}
