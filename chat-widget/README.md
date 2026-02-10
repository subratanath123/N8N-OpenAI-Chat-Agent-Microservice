# Chat Widget - File Attachment Support

A modern, type-safe TypeScript chat widget for integrating with the Multimodal Chat API. Send messages and file attachments to your chatbot with ease.

## 📦 Build Status

✅ **Successfully Built!**

The widget has been compiled to:
- `dist/index.js` - JavaScript module
- `dist/index.d.ts` - TypeScript type definitions

## 🚀 Quick Start

### 1. Build the Widget

```bash
cd chat-widget
npm install
npm run build
```

### 2. Test Locally

Open `example.html` in your browser:
```bash
# Start a local web server
python -m http.server 8000
# Then navigate to http://localhost:8000/example.html
```

Or use Python's built-in server:
```bash
cd chat-widget
python -m http.server 8001
```

### 3. Usage in Your App

```html
<!-- Include the widget -->
<script src="path/to/dist/index.js"></script>

<script>
  // Initialize
  const chat = new ChatWidget({
    chatbotId: 'your-bot-id',
    apiBaseUrl: 'http://localhost:8080/v1/api/n8n/multimodal',
    sessionId: 'optional-session-id'
  });

  // Send message
  const response = await chat.sendMessage({
    message: 'Hello, what can you do?',
    attachments: [/* File objects */]
  });

  // Upload file
  const uploadResponse = await chat.uploadFile(fileObject);

  // List attachments
  const attachments = await chat.listAttachments();

  // Delete attachment
  await chat.deleteAttachment(vectorId);
</script>
```

## 📋 Features

✅ **File Uploads** - Upload files and get fileIds  
✅ **Message Sending** - Send text and file attachments  
✅ **Attachment Management** - List, retrieve, and delete attachments  
✅ **Session Management** - Auto-generates session IDs  
✅ **Error Handling** - Comprehensive error messages  
✅ **Type Safety** - Full TypeScript support  
✅ **Zero Dependencies** - Uses native Fetch API  

## 🏗️ Build Commands

```bash
# Build (compile TypeScript)
npm run build

# Watch mode (auto-rebuild on changes)
npm run dev

# Bundle for production (minified IIFE)
npm run bundle

# Clean build artifacts
npm run clean
```

## 📝 API Reference

### ChatWidget Constructor

```typescript
new ChatWidget(config: ChatWidgetConfig)

config: {
  apiBaseUrl: string;      // Base URL for the chat API
  chatbotId: string;       // Your chatbot ID
  sessionId?: string;      // Optional session ID (auto-generated if omitted)
}
```

### Methods

#### `sendMessage(options)`
Send a message with optional attachments.

```typescript
await chat.sendMessage({
  message: string;
  attachments?: Attachment[];
}): Promise<ChatResponse>
```

#### `uploadFile(file)`
Upload a single file.

```typescript
await chat.uploadFile(file: File): Promise<ChatResponse>
```

#### `listAttachments()`
List all attachments for this chatbot.

```typescript
await chat.listAttachments(): Promise<ChatResponse>
```

#### `deleteAttachment(vectorId)`
Delete an attachment by ID.

```typescript
await chat.deleteAttachment(vectorId: string): Promise<ChatResponse>
```

#### `getSessionId()`
Get the current session ID.

```typescript
chat.getSessionId(): string
```

#### `getChatbotId()`
Get the current chatbot ID.

```typescript
chat.getChatbotId(): string
```

## 📂 File Structure

```
chat-widget/
├── src/
│   └── index.ts           # Main widget source
├── dist/
│   ├── index.js           # Compiled JavaScript
│   └── index.d.ts         # TypeScript definitions
├── package.json           # NPM configuration
├── tsconfig.json          # TypeScript configuration
├── example.html           # Demo page
└── README.md             # This file
```

## 🎨 Example Demo

The `example.html` file includes:
- Beautiful UI with gradient design
- Real-time message display
- File upload interface
- Configuration panel
- Error and success notifications

Just open it in a browser after building!

## 🔧 Configuration

### API Base URL
Update the `apiBaseUrl` in your ChatWidget initialization:
```javascript
const chat = new ChatWidget({
  apiBaseUrl: 'http://localhost:8080/v1/api/n8n/multimodal', // Local
  // or
  apiBaseUrl: 'https://api.example.com/v1/api/n8n/multimodal', // Production
  chatbotId: 'your-bot-id'
});
```

### File Upload Endpoint
The widget uses a separate endpoint for file uploads:
```
POST http://localhost:8080/api/attachments/upload
```

## 🐛 Troubleshooting

### "Failed to fetch" error
- Check that your API server is running
- Verify the `apiBaseUrl` is correct
- Check browser console for CORS errors

### "401 Unauthorized"
- Ensure `chatbotId` and `sessionId` are correct
- Check that you're using the right API endpoint
- Verify CORS is properly configured on the backend

### "File upload failed"
- Check file size limits
- Ensure file format is supported
- Verify network connectivity

## 📦 Distribution

### For NPM Registry
```bash
npm publish
```

### For CDN
```bash
npm run bundle
# Upload dist/chat-widget.min.js to your CDN
```

Then use:
```html
<script src="https://your-cdn.com/chat-widget.min.js"></script>
<script>
  const chat = new ChatWidget({ /* config */ });
</script>
```

## 📄 License

MIT

## 🤝 Support

For issues or questions, please refer to the main documentation:
- `MULTIMODAL_CHAT_FRONTEND_SPECIFICATION.md`
- `MULTIMODAL_CHAT_QUICK_REFERENCE.md`

