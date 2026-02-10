/**
 * File Attachment Chat Widget
 * Integrates with Multimodal Chat API for sending messages with file attachments
 */
interface ChatWidgetConfig {
    apiBaseUrl: string;
    chatbotId: string;
    sessionId?: string;
}
interface Attachment {
    name: string;
    type: string;
    size: number;
    data: string;
}
interface ChatMessage {
    message: string;
    attachments?: Attachment[];
}
interface ChatResponse {
    success: boolean;
    result?: string;
    vectorIdMap?: Record<string, string>;
    vectorAttachments?: any[];
    error?: string;
}
declare class ChatWidget {
    private apiBaseUrl;
    private chatbotId;
    private sessionId;
    constructor(config: ChatWidgetConfig);
    /**
     * Send message with optional file attachments
     */
    sendMessage(options: ChatMessage): Promise<ChatResponse>;
    /**
     * Upload file for attachment
     */
    uploadFile(file: File): Promise<ChatResponse>;
    /**
     * List attachments for this chatbot
     */
    listAttachments(): Promise<ChatResponse>;
    /**
     * Delete attachment
     */
    deleteAttachment(vectorId: string): Promise<ChatResponse>;
    /**
     * Convert File to Base64
     */
    private fileToBase64;
    /**
     * Generate unique session ID
     */
    private generateSessionId;
    /**
     * Get current session ID
     */
    getSessionId(): string;
    /**
     * Get current chatbot ID
     */
    getChatbotId(): string;
}
export default ChatWidget;
export { ChatWidget };
