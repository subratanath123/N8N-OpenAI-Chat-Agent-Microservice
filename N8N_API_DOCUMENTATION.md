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

**Example:**
```bash
curl -X POST "http://localhost:8080/v1/api/n8n/chat/batch?workflowId=my-workflow&webhookUrl=http://localhost:5678/webhook/123" \
  -H "Content-Type: application/json" \
  -d '[
    {"role": "system", "content": "You are a helpful assistant"},
    {"role": "user", "content": "Hello, how are you?"}
  ]'
```

#### 3. Send Message with Session
**POST** `/chat/session`

**Parameters:**
- `workflowId` (query): N8N workflow identifier
- `webhookUrl` (query): N8N webhook URL
- `sessionId` (query, optional): Session identifier (uses authenticated user email if not provided)
- `message` (body): Message object

#### 4. Send Message with Parameters
**POST** `/chat/params`

**Parameters:**
- `workflowId` (query): N8N workflow identifier
- `webhookUrl` (query): N8N webhook URL
- `message` (body): Message object
- `additionalParams` (body, optional): Additional parameters map

#### 5. Send Custom Input
**POST** `/chat/custom`

**Parameters:**
- `customInput` (body): Complete N8NChatInput object

**Example:**
```bash
curl -X POST "http://localhost:8080/v1/api/n8n/chat/custom" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {"role": "user", "content": "Hello"},
    "workflowId": "my-workflow",
    "webhookUrl": "http://localhost:5678/webhook/123",
    "sessionId": "user-session-123",
    "additionalParams": {"temperature": 0.7}
  }'
```

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
