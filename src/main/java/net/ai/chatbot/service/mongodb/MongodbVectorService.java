package net.ai.chatbot.service.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing MongoDB collections and vector indexes
 */
@Slf4j
@Service
public class MongodbVectorService {

    private final MongoTemplate mongoTemplate;

    @Value("${spring.data.mongodb.database}")
    private String databaseName;

    public MongodbVectorService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String getKnowledgebaseCollectionName(String projectId) {
        return "jade-ai-knowledgebase-" + projectId;
    }

    public String getKnowledgebaseVectorIndexName(String projectId) {
        return "jade-ai-vector-index-" + projectId;
    }

    /**
     * Creates a MongoDB collection if it doesn't already exist
     *
     * @param collectionName Name of the collection to create
     * @return MongoCollection instance
     */
    public boolean createMongodbCollection(String collectionName) {
        try {
            log.info("Creating MongoDB collection: {}", collectionName);

            boolean collectionExists = mongoTemplate.getCollectionNames()
                    .contains(collectionName);

            if (!collectionExists) {
                mongoTemplate.createCollection(collectionName);
                log.info("Collection '{}' created successfully", collectionName);
            } else {
                log.info("Collection '{}' already exists", collectionName);
            }

            return true;

        } catch (Exception e) {
            log.error("Error creating MongoDB collection: {}", collectionName, e);
            return false;
        }
    }

    /**
     * Creates a vector search index on a MongoDB collection
     *
     * @param collectionName   Name of the collection
     * @param vectorFieldName  Name of the field containing vector embeddings
     * @param dimensions       Number of dimensions in the vector (e.g., 1536 for OpenAI embeddings)
     * @param similarityMetric Similarity metric to use (e.g., "cosine", "euclidean", "dotProduct")
     * @return Index name
     */
    public String createVectorIndex(String collectionName,
                                    String vectorFieldName,
                                    String vectorIndexName,
                                    int dimensions,
                                    String similarityMetric) {
        try {
            log.info("Creating vector index on collection '{}' for field '{}'",
                    collectionName, vectorFieldName);

            Document vectorSearchDefinition = new Document("type", "vector")
                    .append("path", vectorFieldName)
                    .append("numDimensions", dimensions)
                    .append("similarity", similarityMetric); // "cosine", "euclidean", or "dotProduct"

            Document indexDefinition = new Document("fields",
                    new Document(vectorFieldName, vectorSearchDefinition));

            Document command = new Document("createSearchIndexes", collectionName)
                    .append("indexes", List.of(new Document()
                            .append("name", vectorIndexName)
                            .append("definition", indexDefinition)));

            mongoTemplate.getDb().runCommand(command);

            log.info("✅ Vector search index '{}' created successfully on collection '{}'",
                    vectorIndexName, collectionName);

            return vectorIndexName;

        } catch (Exception e) {
            log.error("❌ Error creating vector index on collection: {}", collectionName, e);
            throw new RuntimeException("Failed to create vector index on collection: " + collectionName, e);
        }
    }
}