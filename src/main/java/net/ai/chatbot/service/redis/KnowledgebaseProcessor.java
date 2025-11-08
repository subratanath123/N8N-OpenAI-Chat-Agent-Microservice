package net.ai.chatbot.service.redis;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.FileUpload;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.mongodb.MongodbVectorService;
import net.ai.chatbot.service.n8n.N8nWebhookService;
import net.ai.chatbot.service.training.WebsiteCrawler;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class KnowledgebaseProcessor implements StreamListener<String, ObjectRecord<String, String>> {

    private final MongoTemplate mongoTemplate;
    private final MongodbVectorService mongodbVectorService;
    private final N8nWebhookService n8nWebhookService;
    private final String knowledgeBaseTrainingWebhookUrl;

    public KnowledgebaseProcessor(MongoTemplate mongoTemplate,
                                  MongodbVectorService mongodbVectorService,
                                  N8nWebhookService n8nWebhookService,
                                  String knowledgeBaseTrainingWebhookUrl) {
        this.mongoTemplate = mongoTemplate;
        this.mongodbVectorService = mongodbVectorService;
        this.n8nWebhookService = n8nWebhookService;
        this.knowledgeBaseTrainingWebhookUrl = knowledgeBaseTrainingWebhookUrl;
    }

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        log.info("Message is consuming for website crawl event {}", record.getId());

        ChatBot chatBot = getChatbot(record);
        if (chatBot == null) return;

        String knowledgebaseCollectionName = mongodbVectorService.getKnowledgebaseCollectionName(chatBot.getId());
        mongodbVectorService.createMongodbCollection(knowledgebaseCollectionName);

        String knowledgebaseVectorIndexName = mongodbVectorService.getKnowledgebaseVectorIndexName(chatBot.getId());
        mongodbVectorService.createMongodbCollection(knowledgebaseVectorIndexName);

        //Training PDF Files
        if (Objects.nonNull(chatBot.getFileIds()) && !chatBot.getFileIds().isEmpty()) {
            getFileList(chatBot.getFileIds())
                    .forEach(fileUpload -> {
                        n8nWebhookService.submitAttachmentToN8nKnowledgebase(
                                fileUpload.getData(),
                                fileUpload.getFileName(),
                                fileUpload.getEmail(),
                                knowledgebaseCollectionName,
                                knowledgebaseVectorIndexName,
                                fileUpload.getContentType(),
                                knowledgeBaseTrainingWebhookUrl
                        );
                    });
        }

        String knowledgebase = "";

        if (Objects.nonNull(chatBot.getQaPairs()) && !chatBot.getQaPairs().isEmpty()) {
            knowledgebase += chatBot.getQaPairs()
                    .stream()
                    .map(qaPair -> "If user Question is: " + qaPair.getQuestion() + ", Your Answer should be: " + qaPair.getAnswer())
                    .collect(Collectors.joining(";"));

        }

        if (Objects.nonNull(chatBot.getAddedTexts()) && !chatBot.getAddedTexts().isEmpty()) {
            knowledgebase += "Another Knowledegbase List: " + String.join(";", chatBot.getAddedTexts());
        }

        //Training Textual Knowledegebase
        if (StringUtils.hasLength(knowledgebase)) {
            n8nWebhookService.submitTextContentToN8nKnowledgebase(
                    knowledgebase,
                    chatBot.getEmail(),
                    knowledgebaseCollectionName,
                    knowledgebaseVectorIndexName,
                    "other knowledgebase",
                    knowledgeBaseTrainingWebhookUrl
            );
        }

        if (Objects.nonNull(chatBot.getAddedWebsites())
                && !chatBot.getAddedWebsites().isEmpty()) {

            chatBot.getAddedWebsites()
                    .forEach(websiteUrl -> crawlWebsite(record, chatBot, websiteUrl, knowledgebaseCollectionName, knowledgebaseVectorIndexName));
        }

    }

    private void crawlWebsite(ObjectRecord<String, String> record,
                              ChatBot chatBot,
                              String websiteUrl,
                              String knowledgebaseCollectionName,
                              String knowledgebaseVectorIndexName) {
        try {
            WebsiteCrawler.crawl(websiteUrl,
                    chatBot.getEmail(),
                    5,
                    50,
                    scrappedData ->
                            n8nWebhookService.submitTextContentToN8nKnowledgebase(
                                    scrappedData.html(),
                                    chatBot.getEmail(),
                                    knowledgebaseCollectionName,
                                    knowledgebaseVectorIndexName,
                                    "other knowledgebase",
                                    knowledgeBaseTrainingWebhookUrl
                            )
            );
        } catch (Exception e) {
            log.info("Message is failed to process for website crawl event {}", record.getId());
        }
    }

    private ChatBot getChatbot(ObjectRecord<String, String> record) {
        String chatbotId = record.getValue();

        return mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("id").is(chatbotId)),
                ChatBot.class
        );
    }

    private List<FileUpload> getFileList(List<String> fileIdList) {
        return mongoTemplate.find(
                new Query().addCriteria(Criteria.where("id").in(fileIdList)),
                FileUpload.class
        );
    }
}