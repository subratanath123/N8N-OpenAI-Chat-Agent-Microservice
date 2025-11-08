package net.ai.chatbot.service.embeddedChat;

import org.springframework.stereotype.Repository;

@Repository
public class EmbeddedChatService {

    private static final String webhookUrl = "http://143.198.58.6:5678/webhook/beab6fcf-f27a-4d26-8923-5f95e8190fea";
    private static final String workflowId= "default-workflow";

    /*
    * have to move this in environemnt specific config file or in DB ..SO that we can distinguish each environment
    */
    public String getWorkflowId() {
        return workflowId;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

}
