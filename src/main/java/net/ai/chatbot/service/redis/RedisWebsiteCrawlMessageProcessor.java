package net.ai.chatbot.service.redis;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.ProjectTrainingInfo;
import net.ai.chatbot.entity.WebsiteTrainEvent;
import net.ai.chatbot.service.pinnecone.PineconeService;
import net.ai.chatbot.service.training.WebsiteCrawler;
import net.ai.chatbot.utils.VectorDatabaseUtils;
import org.springframework.ai.document.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;

import java.util.Date;
import java.util.List;
import java.util.Map;


@AllArgsConstructor
@Slf4j
public class RedisWebsiteCrawlMessageProcessor implements StreamListener<String, ObjectRecord<String, WebsiteTrainEvent>> {

    private final PineconeService pineconeService;
    private final MongoTemplate mongoTemplate;

    @Override
    public void onMessage(ObjectRecord<String, WebsiteTrainEvent> record) {

        log.info("Message is consuming for website crawl event {}", record.getId());

        try {
            WebsiteCrawler.crawl(record.getValue(), o -> {
                List<Document> documents = VectorDatabaseUtils.getSplittedDocuments(
                        o.html(),
                        Map.of(
                                "The Url of current html content", o.url(),
                                "Page Title", o.title()
                        ));

                WebsiteTrainEvent trainEvent = record.getValue();

                pineconeService.storeDocument(
                        VectorDatabaseUtils.getNameSpace(trainEvent.email(), trainEvent.projectName()),
                        documents);

                ProjectTrainingInfo trainingInfo = ProjectTrainingInfo.builder()
                        .projectName(trainEvent.projectName())
                        .projectId(trainEvent.projectId())
                        .childPageUrl(o.url())
                        .created(new Date())
                        .build();

                mongoTemplate.save(trainingInfo);

            });
        } catch (Exception e) {
            log.info("Message is failed to process for website crawl event {}", record.getId());
        }
    }
}