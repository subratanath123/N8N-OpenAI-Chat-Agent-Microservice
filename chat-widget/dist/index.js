/**
 * File Attachment Chat Widget
 * Integrates with Multimodal Chat API for sending messages with file attachments
 */
class ChatWidget {
    constructor(config) {
        this.apiBaseUrl = config.apiBaseUrl;
        this.chatbotId = config.chatbotId;
        this.sessionId = config.sessionId || this.generateSessionId();
        console.log('🎉 Chat Widget Initialized');
        console.log('- Bot ID:', this.chatbotId);
        console.log('- Session ID:', this.sessionId);
        console.log('- API Base:', this.apiBaseUrl);
    }
    /**
     * Send message with optional file attachments
     */
    async sendMessage(options) {
        try {
            console.log('📨 Sending message...');
            const requestBody = {
                message: options.message,
                chatbotId: this.chatbotId,
                sessionId: this.sessionId,
                attachments: options.attachments || []
            };
            console.log('Request:', {
                message: options.message,
                files: options.attachments?.length || 0,
                chatbotId: this.chatbotId
            });
            const response = await fetch(`${this.apiBaseUrl}/anonymous/chat`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });
            if (!response.ok) {
                throw new Error(`API error: ${response.status} ${response.statusText}`);
            }
            const data = await response.json();
            console.log('✅ Message sent successfully');
            return data;
        }
        catch (error) {
            console.error('❌ Error sending message:', error);
            return {
                success: false,
                error: String(error)
            };
        }
    }
    /**
     * Upload file for attachment
     */
    async uploadFile(file) {
        try {
            console.log('📤 Uploading file:', file.name);
            const base64Data = await this.fileToBase64(file);
            const formData = new FormData();
            formData.append('file', file);
            formData.append('chatbotId', this.chatbotId);
            formData.append('sessionId', this.sessionId);
            const response = await fetch('http://localhost:8080/api/attachments/upload', {
                method: 'POST',
                body: formData
            });
            if (!response.ok) {
                throw new Error(`Upload failed: ${response.status}`);
            }
            const data = await response.json();
            console.log('✅ File uploaded:', data.fileId);
            return {
                success: true,
                result: data.fileId,
                vectorAttachments: [data]
            };
        }
        catch (error) {
            console.error('❌ Error uploading file:', error);
            return {
                success: false,
                error: String(error)
            };
        }
    }
    /**
     * List attachments for this chatbot
     */
    async listAttachments() {
        try {
            const response = await fetch(`${this.apiBaseUrl}/attachments/${this.chatbotId}`, { method: 'GET' });
            if (!response.ok) {
                throw new Error(`Failed to list attachments: ${response.status}`);
            }
            const data = await response.json();
            console.log('📋 Attachments:', data);
            return { success: true, vectorAttachments: data };
        }
        catch (error) {
            console.error('❌ Error listing attachments:', error);
            return { success: false, error: String(error) };
        }
    }
    /**
     * Delete attachment
     */
    async deleteAttachment(vectorId) {
        try {
            const response = await fetch(`${this.apiBaseUrl}/attachments/${this.chatbotId}/${vectorId}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(`Failed to delete attachment: ${response.status}`);
            }
            console.log('✅ Attachment deleted:', vectorId);
            return { success: true };
        }
        catch (error) {
            console.error('❌ Error deleting attachment:', error);
            return { success: false, error: String(error) };
        }
    }
    /**
     * Convert File to Base64
     */
    fileToBase64(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => {
                const result = reader.result;
                const base64 = result.split(',')[1];
                resolve(base64);
            };
            reader.onerror = reject;
            reader.readAsDataURL(file);
        });
    }
    /**
     * Generate unique session ID
     */
    generateSessionId() {
        return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }
    /**
     * Get current session ID
     */
    getSessionId() {
        return this.sessionId;
    }
    /**
     * Get current chatbot ID
     */
    getChatbotId() {
        return this.chatbotId;
    }
}
// Export for use in browsers and Node.js
export default ChatWidget;
export { ChatWidget };
