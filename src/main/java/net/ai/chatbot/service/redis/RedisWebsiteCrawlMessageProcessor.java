package net.ai.chatbot.service.redis;

import net.ai.chatbot.entity.WebsiteTrainEvent;
import net.ai.chatbot.service.pinnecone.PineconeService;
import net.ai.chatbot.service.training.WebsiteCrawler;
import net.ai.chatbot.utils.VectorDatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service
public class RedisWebsiteCrawlMessageProcessor implements StreamListener<String, ObjectRecord<String, WebsiteTrainEvent>> {

    private Logger log = LoggerFactory.getLogger(RedisWebsiteCrawlMessageProcessor.class);

    private final PineconeService pineconeService;

    public RedisWebsiteCrawlMessageProcessor(PineconeService pineconeService) {
        this.pineconeService = pineconeService;
    }

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

                pineconeService.storeDocument(
                        VectorDatabaseUtils.getNameSpace(record.getValue().email(), "project"),
                        documents);

            });
        } catch (Exception e) {
            log.info("Message is failed to process for website crawl event {}", record.getId());
        }
    }
}