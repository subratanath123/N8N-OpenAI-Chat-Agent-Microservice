package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.service.n8n.GenericN8NService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${n8n.webhook.knowledgebase.chat.url}")
    private String webhookUrl;

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
    @PostMapping("/chat/")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(
            @RequestBody Message message) {
        
        N8NChatResponse<Object> response = n8nService.sendMessage(message, webhookUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Send multiple messages to N8N workflow
     */
    @PostMapping("/chat/batch")
    public ResponseEntity<N8NChatResponse<Object>> sendMessages(
            @RequestBody List<Message> messages,
            @RequestParam String webhookUrl) {
        
        N8NChatResponse<Object> response = n8nService.sendMessages(messages, webhookUrl);
        
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
        
        N8NChatResponse<Object> response = n8nService.sendMessageWithSession(message, currentSessionId, webhookUrl);
        
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

}
