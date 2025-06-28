package net.ai.chatbot.service.redis;

import net.ai.chatbot.entity.WebsiteTrainEvent;
import net.ai.chatbot.service.pinnecone.PineconeService;
import net.ai.chatbot.service.training.WebsiteCrawler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service
public class RedisWebsiteCrawlMessageProcessor implements StreamListener<String, ObjectRecord<String, WebsiteTrainEvent>> {

    private Logger log = LoggerFactory.getLogger(RedisWebsiteCrawlMessageProcessor.class);

    @Autowired
    private PineconeService pineconeService;

    @Override
    public void onMessage(ObjectRecord<String, WebsiteTrainEvent> record) {

        log.info("Message is consuming for website crawl event {}", record.getId());

        try {
            WebsiteCrawler.crawl(record.getValue(), o -> {

                pineconeService.storeDocument(
                        record.getValue().email()
                                + ":"
                                + "websiteData"
                                + ":"
                                + record.getValue().websiteUrl(),
                        List.of(new Document(o.scrappedData(), Map.of(o.title(), o.title()))));

            });
        } catch (Exception e) {
            log.info("Message is failed to process for website crawl event {}", record.getId());
        }
    }
}