package net.ai.chatbot.service.training;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.WebsiteTrainEvent;
import net.ai.chatbot.service.pinnecone.PineconeVectorStoreFactory;
import net.ai.chatbot.utils.AuthUtils;
import net.ai.chatbot.utils.Utils;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static net.ai.chatbot.constants.Constants.TRAIN_WEBSITE_EVENT_STREAM;
import static net.ai.chatbot.utils.VectorDatabaseUtils.getNameSpace;
import static net.ai.chatbot.utils.VectorDatabaseUtils.getSplittedDocuments;

@Service
@Slf4j
@AllArgsConstructor
public class ChatBotTrainingService {

    private final PineconeVectorStoreFactory pineconeVectorStoreFactory;

    private final RedisTemplate<String, String> redisTemplate;

    public void handleWebsiteUrlTraining(String projectId, String webSite, String projectName) {
        WebsiteTrainEvent websiteTrainEvent = new WebsiteTrainEvent(projectId, AuthUtils.getEmail(), projectName, webSite, Utils.extractBaseUrl(webSite));

        ObjectRecord<String, WebsiteTrainEvent> record = StreamRecords
                .newRecord()
                .ofObject(websiteTrainEvent)
                .withStreamKey(TRAIN_WEBSITE_EVENT_STREAM);

        RecordId recordId = this.redisTemplate.opsForStream().add(record);

        log.info("Creating websiteTrainEvent... recordId:{}", recordId);
    }

    public void handleFileTraining(MultipartFile file, String projectName) throws IOException, TikaException {
        List<Document> smallDocs = getSplittedDocuments(file);

        pineconeVectorStoreFactory.createForNamespace(getNameSpace(AuthUtils.getEmail(), projectName)).add(smallDocs);
    }

    public void handleTextBasedTraining(String description, String projectName) throws IOException, TikaException {
        List<Document> smallDocs = getSplittedDocuments(description);

        pineconeVectorStoreFactory.createForNamespace(getNameSpace(AuthUtils.getEmail(), projectName)).add(smallDocs);
    }
}
