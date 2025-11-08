package net.ai.chatbot.service.aichatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.FileUpload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class FileUploadService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public FileUpload load(String id) {
        Query query = new Query()
                .addCriteria(Criteria.where("id").is(id));

        return Optional.ofNullable(mongoTemplate.findOne(query, FileUpload.class))
                .orElse(null);
    }

    public FileUpload save(FileUpload fileUpload) {
        return mongoTemplate.save(fileUpload);
    }

}

