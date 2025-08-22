# N8N API Controller Documentation

## Overview

This document describes the new N8N API controller and reusable generic classes for handling N8N workflow communications in the Chat API application.

## Architecture

### Generic Classes

#### 1. N8NChatInput<T>
A generic input class that can handle different types of messages for N8N workflows.

**Features:**
- Generic type support for different message formats
- Built-in workflow configuration
- Additional parameters support
- Session management
- Optional file attachments support
- Builder pattern for easy construction

**Usage:**
```java
// Simple message
N8NChatInput<Message> input = N8NChatInput.of(message)
    .withWorkflow("workflow-id", "webhook-url");

// With additional parameters
Map<String, Object> params = new HashMap<>();
params.put("temperature", 0.7);
N8NChatInput<Message> input = N8NChatInput.of(message)
    .withWorkflow("workflow-id", "webhook-url")
    .withAdditionalParams(params);

// With attachments
List<Attachment> attachments = Arrays.asList(attachment);
N8NChatInput<Message> inputWithAttachments = N8NChatInput.of(message)
    .withWorkflow("workflow-id", "webhook-url")
    .withAdditionalParams(params)
    .withAttachments(attachments);
```

#### 2. N8NChatResponse<T>
A generic response class that handles different response formats from N8N workflows.

**Features:**
- Success/error status tracking
- Generic data type support
- Metadata support
- Timestamp tracking
- Error code and message handling

**Usage:**
```java
// Success response
N8NChatResponse<String> response = N8NChatResponse.success("Response data");

// Error response
N8NChatResponse<String> response = N8NChatResponse.error("Error message");
```

#### 3. Attachment
A DTO class for representing file attachments in chat messages.

**Fields:**
- `name`: File name (e.g., "screenshot.png")
- `size`: File size in bytes
- `type`: MIME type (e.g., "image/png", "application/pdf")
- `base64`: Base64-encoded file content

**Usage:**
```java
Attachment attachment = Attachment.builder()
    .name("document.pdf")
    .size(102400L)
    .type("application/pdf")
    .base64("JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovUGFnZXMgMiAwIFIKPj4KZW5kb2JqCg==")
    .build();
```

#### 4. ChatRequest
A DTO class for chat requests that include attachments and additional parameters.

**Fields:**
- `message`: User's message content
- `attachments`: List of file attachments
- `sessionId`: Session identifier
- `webhookUrl`: N8N webhook URL
- `workflowId`: N8N workflow identifier
- `additionalParams`: Additional parameters for the workflow

**Usage:**
```java
ChatRequest request = ChatRequest.builder()
    .message("Please analyze this document")
    .attachments(Arrays.asList(attachment))
    .sessionId("session_123")
    .workflowId("default-workflow")
    .webhookUrl("http://localhost:5678/webhook/123")
    .additionalParams(Map.of("temperature", 0.7, "model", "gpt-4"))
    .build();
```

### Services

#### 1. GenericN8NService<T, R>
Interface defining methods for N8N workflow communication.

**Methods:**
- `sendMessage(T message, String workflowId, String webhookUrl)`
- `sendMessages(List<T> messages, String workflowId, String webhookUrl)`
- `sendCustomInput(N8NChatInput<T> input)`
- `sendMessageWithSession(T message, String sessionId, String workflowId, String webhookUrl)`
- `sendMessageWithParams(T message, Map<String, Object> additionalParams, String workflowId, String webhookUrl)`

#### 2. GenericN8NServiceImpl<T, R>
Implementation of the generic N8N service with HTTP communication.

#### 3. N8NChatService
Specific implementation that extends the generic service and provides backward compatibility with existing ChatService interface.

## API Endpoints

### Base URL: `/v1/api/n8n`

#### 1. Send Single Message
**POST** `/chat`

**Parameters:**
- `workflowId` (query): N8N workflow identifier
- `webhookUrl` (query): N8N webhook URL
- `message` (body): Message object

**Example:**
```bash
curl -X POST "http://localhost:8080/v1/api/n8n/chat?workflowId=my-workflow&webhookUrl=http://localhost:5678/webhook/123" \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "content": "Hello, how are you?"
  }'
```

#### 2. Send Multiple Messages
**POST** `/chat/batch`

**Parameters:**
- `workflowId` (query): N8N workflow identifier
- `webhookUrl` (query): N8N webhook URL
- `messages` (body): Array of message objects

#### 3. Send Custom Input with Optional Attachments
**POST** `/chat/custom`

**Description:** Sends a custom chat input with full control over the request. Now supports optional file attachments - takes the first attachment if present and includes it in the additional parameters sent to N8N.

**Request Body:**
```json
{
  "message": {
    "role": "user",
    "content": "Please analyze this screenshot"
  },
  "attachments": [
    {
      "name": "Screenshot from 2025-08-10 22-08-43.png",
      "size": 81442,
      "type": "image/png",
      "base64": "iVBORw0KGgoAAAANSUhEUgAABlEAAAK8CAYAAAB/f5kAAAAAB..."
    }
  ],
  "sessionId": "session_1754764089953_mmwzjbuh2",
  "webhookUrl": "http://localhost:5678/webhook/beab6fcf-f27a-4d26-8923-5f95e8190fea",
  "workflowId": "default-workflow",
  "additionalParams": {
    "temperature": 0.7,
    "systemPrompt": "You are a helpful AI assistant.",
    "model": "gpt-4"
  }
}
```

**Response:** The first attachment from the list is automatically extracted and sent to N8N in the `additionalParams` with the key `"attachment"`. The attachment data includes:

- `binary`: The actual file data as binary bytes (converted from base64)
- `fileName`: Original filename
- `fileSize`: File size in bytes  
- `mimeType`: MIME type (e.g., "image/png", "application/pdf")

**Example of what N8N receives:**
```json
{
  "message": {
    "role": "user",
    "content": "Please analyze this screenshot"
  },
  "additionalParams": {
    "temperature": 0.7,
    "systemPrompt": "You are a helpful AI assistant.",
    "model": "gpt-4",
    "attachment": {
      "binary": [binary_data_bytes],
      "fileName": "Screenshot from 2025-08-10 22-08-43.png",
      "fileSize": 81442,
      "mimeType": "image/png"
    }
  },
  "workflowId": "default-workflow",
  "webhookUrl": "http://localhost:5678/webhook/beab6fcf-f27a-4d26-8923-5f95e8190fea"
}
```

**Example:**
```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chat/custom" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "role": "user",
      "content": "Please analyze this screenshot"
    },
    "attachments": [
      {
        "name": "screenshot.png",
        "size": 81442,
        "type": "image/png",
        "base64": "iVBORw0KGgoAAAANSUhEUgAABlEAAAK8CAYAAAB/f5kAAAAAB..."
      }
    ],
    "workflowId": "default-workflow",
    "webhookUrl": "http://localhost:5678/webhook/123",
    "additionalParams": {
      "temperature": 0.7,
      "model": "gpt-4"
    }
  }'
```

**Note:** The `attachments` field is optional. If no attachments are provided, the request will be processed normally without attachment processing. The N8N service automatically processes attachments, converts base64 data to binary bytes, and formats them properly for N8N workflows.

#### 4. Send Message with Session
**POST** `/chat/session`

**Parameters:**
- `workflowId` (query): N8N workflow identifier
- `webhookUrl` (query): N8N webhook URL
- `sessionId` (query, optional): Session identifier (uses authenticated user email if not provided)
- `message` (body): Message object

#### 5. Send Message with Parameters
**POST** `/chat/params`

**Parameters:**
- `workflowId` (query): N8N workflow identifier
- `webhookUrl` (query): N8N webhook URL
- `message` (body): Message object
- `additionalParams` (body, optional): Additional parameters map



#### 6. Health Check
**GET** `/health/{workflowId}`

**Parameters:**
- `workflowId` (path): N8N workflow identifier
- `webhookUrl` (query): N8N webhook URL

#### 7. Workflow Status
**GET** `/status/{workflowId}`

**Parameters:**
- `workflowId` (path): N8N workflow identifier
- `webhookUrl` (query): N8N webhook URL

## Configuration

### Application Properties

Add the following properties to `application.yml`:

```yaml
n8n:
  default:
    webhook-url: http://localhost:5678/webhook-test/beab6fcf-f27a-4d26-8923-5f95e8190fea
    workflow-id: default-workflow
```

### Bean Configuration

The N8N services are automatically configured via `N8NConfig` class:

```java
@Configuration
public class N8NConfig {
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public GenericN8NService<Message, Object> n8nService() {
        return new GenericN8NServiceImpl<>();
    }
}
```

## Response Format

All endpoints return responses in the following format:

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": "Response data from N8N workflow",
  "choices": [],
  "metadata": {},
  "workflowId": "workflow-id",
  "timestamp": 1640995200000,
  "errorCode": null,
  "errorMessage": null
}
```

### Error Response Example

```json
{
  "success": false,
  "message": null,
  "data": null,
  "choices": [],
  "metadata": {},
  "workflowId": null,
  "timestamp": 1640995200000,
  "errorCode": "HTTP_ERROR",
  "errorMessage": "HTTP error calling N8N webhook: Connection refused"
}
```

## Usage Examples

### Java Service Usage

```java
@Autowired
private GenericN8NService<Message, Object> n8nService;

// Send simple message
Message message = Message.builder()
    .role("user")
    .content("Hello")
    .build();

N8NChatResponse<Object> response = n8nService.sendMessage(
    message, 
    "workflow-id", 
    "http://localhost:5678/webhook/123"
);

// Send with session
N8NChatResponse<Object> response = n8nService.sendMessageWithSession(
    message, 
    "session-123", 
    "workflow-id", 
    "http://localhost:5678/webhook/123"
);
```

### Custom Input Usage

```java
N8NChatInput<Message> input = N8NChatInput.<Message>of(message)
    .withWorkflow("workflow-id", "webhook-url")
    .withAdditionalParams(Map.of("temperature", 0.7));

N8NChatResponse<Object> response = n8nService.sendCustomInput(input);
```

## Error Handling

The service includes comprehensive error handling:

1. **HTTP Errors**: Network connectivity issues, invalid URLs
2. **N8N Errors**: Workflow execution failures
3. **Validation Errors**: Missing required parameters
4. **Unexpected Errors**: General exceptions

All errors are logged and returned with appropriate error codes and messages.

## Security

- CORS is enabled for all origins (configurable)
- Authentication is handled via `AuthUtils.getEmail()`
- Session management is supported
- Input validation is performed

## Migration from Existing N8N Service

The new implementation is backward compatible. Existing code using `N8NService` can continue to work, or you can migrate to use the new generic service:

```java
// Old way
@Autowired
private N8NService n8nService;

// New way
@Autowired
private GenericN8NService<Message, Object> n8nService;
// or
@Autowired
private N8NChatService n8nChatService;
```
