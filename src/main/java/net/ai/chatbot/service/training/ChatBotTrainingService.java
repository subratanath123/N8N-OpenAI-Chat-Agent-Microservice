package net.ai.chatbot.service.training;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.WebsiteTrainEvent;
import net.ai.chatbot.service.pinnecone.PineconeVectorStoreFactory;
import net.ai.chatbot.utils.AuthUtils;
import net.ai.chatbot.utils.Utils;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static net.ai.chatbot.constants.Constants.TRAIN_WEBSITE_EVENT_STREAM;
import static net.ai.chatbot.utils.VectorDatabaseUtils.getNameSpace;

@Service
@Slf4j
public class ChatBotTrainingService {

    private final PineconeVectorStoreFactory pineconeVectorStoreFactory;

    private final RedisTemplate<String, String> redisTemplate;

    public ChatBotTrainingService(PineconeVectorStoreFactory pineconeVectorStoreFactory,
                                  RedisTemplate<String, String> redisTemplate) {
        this.pineconeVectorStoreFactory = pineconeVectorStoreFactory;
        this.redisTemplate = redisTemplate;
    }

    public void handleWebsiteUrlTraining(String webSite) {
        WebsiteTrainEvent websiteTrainEvent = new WebsiteTrainEvent(AuthUtils.getEmail(), webSite, Utils.extractBaseUrl(webSite));

        ObjectRecord<String, WebsiteTrainEvent> record = StreamRecords
                .newRecord()
                .ofObject(websiteTrainEvent)
                .withStreamKey(TRAIN_WEBSITE_EVENT_STREAM);

        RecordId recordId = this.redisTemplate.opsForStream().add(record);

        log.info("Creating websiteTrainEvent... recordId:{}", recordId);
    }

    public void handleFileTraining(MultipartFile file) throws IOException, TikaException {
        Tika tika = new Tika();
        String text = tika.parseToString(file.getInputStream());
        Document document = new Document(text);

        TextSplitter splitter = new TokenTextSplitter(true);
        List<Document> smallDocs = splitter.split(document);

        pineconeVectorStoreFactory.createForNamespace(getNameSpace(AuthUtils.getEmail(), "project")).add(smallDocs);
    }

    public void handleTextBasedTraining(String description) throws IOException {
        Document document = new Document(description);

        TextSplitter splitter = new TokenTextSplitter(true);
        List<Document> smallDocs = splitter.split(document);

        pineconeVectorStoreFactory.createForNamespace(getNameSpace(AuthUtils.getEmail(),"project")).add(smallDocs);
    }
}
