package net.ai.chatbot.controller;

import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatInput;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.service.n8n.GenericN8NService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserChatN8NControllerTest {

    @Mock
    private GenericN8NService<Message, Object> n8nService;

    @InjectMocks
    private AuthenticatedUserChatN8NController controller;

    private Message testMessage;
    private Attachment testAttachment;
    private N8NChatInput<Message> testInput;

    @BeforeEach
    void setUp() {
        testMessage = Message.builder()
                .role("user")
                .message("Test message with attachment")
                .build();

        testAttachment = Attachment.builder()
                .name("Senior Software Engineer Decmber 2024.pdf")
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
                .webhookUrl("http://localhost:5678/webhook/beab6fcf-f27a-4d26-8923-5f95e8190fea")
                .additionalParams(additionalParams)
                .build();
    }

    @Test
    void testSendCustomInput_WithAttachments() {
        // Arrange
        N8NChatResponse<Object> mockResponse = new N8NChatResponse<>();
        when(n8nService.sendCustomInput(any(N8NChatInput.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendCustomInput(testInput);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        
        // Verify that the service was called with the input containing attachments
        verify(n8nService).sendCustomInput(eq(testInput));
    }

    @Test
    void testSendCustomInput_WithoutAttachments() {
        // Arrange
        N8NChatInput<Message> inputWithoutAttachments = N8NChatInput.<Message>builder()
                .message(testMessage)
                .workflowId("test-workflow")
                .webhookUrl("http://test-webhook.com")
                .additionalParams(Map.of("temperature", 0.7))
                .build();
        
        N8NChatResponse<Object> mockResponse = new N8NChatResponse<>();
        when(n8nService.sendCustomInput(any(N8NChatInput.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendCustomInput(inputWithoutAttachments);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        
        // Verify that the service was called with the input without attachments
        verify(n8nService).sendCustomInput(eq(inputWithoutAttachments));
    }

    @Test
    void testSendFileToN8N_Success() throws Exception {
        // Arrange
        MockMultipartFile mockFile = new MockMultipartFile(
            "file", 
            "test.pdf", 
            "application/pdf", 
            "test content".getBytes()
        );
        
        N8NChatResponse<Object> mockResponse = new N8NChatResponse<>();
        mockResponse.setSuccess(true);
        when(n8nService.sendCustomInput(any(N8NChatInput.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendFileToN8N(
            mockFile, "test-workflow", "http://test-webhook.com", "Test message", "test-session"
        );

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        
        // Verify that the service was called
        verify(n8nService).sendCustomInput(any(N8NChatInput.class));
    }

    @Test
    void testSendFileToN8N_WithDefaultMessage() throws Exception {
        // Arrange
        MockMultipartFile mockFile = new MockMultipartFile(
            "file", 
            "document.docx", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
            "test content".getBytes()
        );
        
        N8NChatResponse<Object> mockResponse = new N8NChatResponse<>();
        mockResponse.setSuccess(true);
        when(n8nService.sendCustomInput(any(N8NChatInput.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendFileToN8N(
            mockFile, "test-workflow", "http://test-webhook.com", null, null
        );

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        
        // Verify that the service was called
        verify(n8nService).sendCustomInput(any(N8NChatInput.class));
    }

    @Test
    void testSendMultipleFilesToN8N_Success() throws Exception {
        // Arrange
        MockMultipartFile file1 = new MockMultipartFile(
            "files", 
            "test1.pdf", 
            "application/pdf", 
            "test content 1".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
            "files", 
            "test2.docx", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
            "test content 2".getBytes()
        );
        
        List<MockMultipartFile> files = Arrays.asList(file1, file2);
        
        N8NChatResponse<Object> mockResponse = new N8NChatResponse<>();
        mockResponse.setSuccess(true);
        when(n8nService.sendCustomInput(any(N8NChatInput.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendMultipleFilesToN8N(
            (List) files, "test-workflow", "http://test-webhook.com", "Processing multiple files", "test-session"
        );

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        
        // Verify that the service was called
        verify(n8nService).sendCustomInput(any(N8NChatInput.class));
    }

    @Test
    void testSendFileToN8N_IOException() throws Exception {
        // Arrange
        MockMultipartFile mockFile = new MockMultipartFile(
            "file", 
            "test.pdf", 
            "application/pdf", 
            "test content".getBytes()
        );
        
        // Simulate IOException by creating a file that throws exception when getBytes() is called
        MockMultipartFile problematicFile = spy(mockFile);
        doThrow(new IOException("File read error")).when(problematicFile).getBytes();

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendFileToN8N(
            problematicFile, "test-workflow", "http://test-webhook.com", "Test message", "test-session"
        );

        // Assert
        assertNotNull(response);
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("FILE_PROCESSING_ERROR", response.getBody().getErrorCode());
        
        // Verify that the service was not called
        verify(n8nService, never()).sendCustomInput(any(N8NChatInput.class));
    }

    @Test
    void testSendFileDirectlyToN8N_Success() throws Exception {
        // Arrange
        MockMultipartFile mockFile = new MockMultipartFile(
            "file", 
            "test.pdf", 
            "application/pdf", 
            "test content".getBytes()
        );

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendFileDirectlyToN8N(
            mockFile, "test-workflow", "http://test-webhook.com", "Test message", "test-session"
        );

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("test-workflow", response.getBody().getWorkflowId());
    }

    @Test
    void testSendMultipleFilesDirectlyToN8N_Success() throws Exception {
        // Arrange
        MockMultipartFile file1 = new MockMultipartFile(
            "files", 
            "test1.pdf", 
            "application/pdf", 
            "test content 1".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
            "files", 
            "test2.docx", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
            "test content 2".getBytes()
        );
        
        List<MockMultipartFile> files = Arrays.asList(file1, file2);

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendMultipleFilesDirectlyToN8N(
            (List) files, "test-workflow", "http://test-webhook.com", "Processing multiple files", "test-session"
        );

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("test-workflow", response.getBody().getWorkflowId());
    }

    @Test
    void testSendFileDirectlyToN8N_WithDefaultMessage() throws Exception {
        // Arrange
        MockMultipartFile mockFile = new MockMultipartFile(
            "file", 
            "document.docx", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
            "test content".getBytes()
        );

        // Act
        ResponseEntity<N8NChatResponse<Object>> response = controller.sendFileDirectlyToN8N(
            mockFile, "test-workflow", "http://test-webhook.com", null, null
        );

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("test-workflow", response.getBody().getWorkflowId());
    }
}
