package net.ai.chatbot.service.n8n;

import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenericN8NServiceTest {

    @Mock
    private RestTemplate n8nRestTemplate;

    @InjectMocks
    private GenericN8NService<Message, Object> n8nService;

    private Message testMessage;
    private Attachment testAttachment;
    private N8NChatInput<Message> testInput;

    @BeforeEach
    void setUp() {
        testMessage = Message.builder()
                .role("user")
                .content("Test message with attachment")
                .build();

        // Create a test PDF with known base64 content
        testAttachment = Attachment.builder()
                .name("test-document.pdf")
                .size(18951L)
                .type("application/pdf")
                .base64("JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovUGFnZXMgMiAwIFIKPj4KZW5kb2JqCg==")
                .build();

        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("temperature", 0.7);
        additionalParams.put("model", "gpt-4");

        testInput = N8NChatInput.<Message>builder()
                .message(testMessage)
                .attachments(Arrays.asList(testAttachment))
                .workflowId("default-workflow")
                .webhookUrl("http://localhost:5678/webhook/test")
                .additionalParams(additionalParams)
                .build();
    }

    @Test
    void testSendCustomInput_WithAttachment_ConvertsBase64ToBinary() {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        mockResponse.put("message", "File processed successfully");
        
        when(n8nRestTemplate.postForObject(anyString(), any(), eq(Object.class)))
                .thenReturn(mockResponse);

        // Act
        N8NChatResponse<Object> response = n8nService.sendCustomInput(testInput);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        
        // Verify that RestTemplate was called with the correct data
        verify(n8nRestTemplate).postForObject(
                eq("http://localhost:5678/webhook/test"),
                argThat(input -> {
                    if (!(input instanceof N8NChatInput)) {
                        return false;
                    }
                    
                    N8NChatInput<?> processedInput = (N8NChatInput<?>) input;
                    Map<String, Object> params = processedInput.getAdditionalParams();
                    
                    if (params == null || !params.containsKey("attachment")) {
                        return false;
                    }
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attachment = (Map<String, Object>) params.get("attachment");
                    
                    // Check that binary data is present and not base64
                    return attachment.containsKey("binary") &&
                           attachment.containsKey("fileName") &&
                           attachment.containsKey("fileSize") &&
                           attachment.containsKey("mimeType") &&
                           !attachment.containsKey("base64") &&
                           attachment.get("binary") instanceof byte[] &&
                           ((byte[]) attachment.get("binary")).length > 0;
                }),
                eq(Object.class)
        );
    }

    @Test
    void testSendCustomInput_WithoutAttachment_NoProcessing() {
        // Arrange
        N8NChatInput<Message> inputWithoutAttachments = N8NChatInput.<Message>builder()
                .message(testMessage)
                .workflowId("test-workflow")
                .webhookUrl("http://localhost:5678/webhook/test")
                .additionalParams(Map.of("temperature", 0.7))
                .build();
        
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        
        when(n8nRestTemplate.postForObject(anyString(), any(), eq(Object.class)))
                .thenReturn(mockResponse);

        // Act
        N8NChatResponse<Object> response = n8nService.sendCustomInput(inputWithoutAttachments);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        
        // Verify that RestTemplate was called with the original input unchanged
        verify(n8nRestTemplate).postForObject(
                eq("http://localhost:5678/webhook/test"),
                eq(inputWithoutAttachments),
                eq(Object.class)
        );
    }

    @Test
    void testSendCustomInput_InvalidBase64_ReturnsError() {
        // Arrange
        Attachment invalidAttachment = Attachment.builder()
                .name("invalid.pdf")
                .size(100L)
                .type("application/pdf")
                .base64("invalid-base64-data!@#") // Invalid base64
                .build();
        
        N8NChatInput<Message> invalidInput = N8NChatInput.<Message>builder()
                .message(testMessage)
                .attachments(Arrays.asList(invalidAttachment))
                .workflowId("test-workflow")
                .webhookUrl("http://localhost:5678/webhook/test")
                .additionalParams(new HashMap<>())
                .build();

        // Act
        N8NChatResponse<Object> response = n8nService.sendCustomInput(invalidInput);

        // Assert
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("INVALID_BASE64", response.getErrorCode());
        assertTrue(response.getErrorMessage().contains("Invalid base64 data"));
        
        // Verify that RestTemplate was never called
        verify(n8nRestTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void testSendCustomInput_HttpError_ReturnsError() {
        // Arrange
        when(n8nRestTemplate.postForObject(anyString(), any(), eq(Object.class)))
                .thenThrow(new RestClientException("Connection failed"));

        // Act
        N8NChatResponse<Object> response = n8nService.sendCustomInput(testInput);

        // Assert
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("HTTP_ERROR", response.getErrorCode());
        assertTrue(response.getErrorMessage().contains("HTTP error calling N8N webhook"));
    }
}
