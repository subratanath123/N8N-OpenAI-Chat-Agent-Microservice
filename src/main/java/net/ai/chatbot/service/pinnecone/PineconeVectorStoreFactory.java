package net.ai.chatbot.service.pinnecone;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;

public class PineconeVectorStoreFactory {

    private final String apiKey;
    private final String indexName;
    private final EmbeddingModel embeddingModel;

    public PineconeVectorStoreFactory(String apiKey, String indexName, EmbeddingModel embeddingModel) {
        this.apiKey = apiKey;
        this.indexName = indexName;
        this.embeddingModel = embeddingModel;
    }

    public VectorStore createForNamespace(String namespace) {
        return PineconeVectorStore.builder(embeddingModel)
                .apiKey(apiKey)
                .indexName(indexName)
                .namespace(namespace)
                .build();
    }
}
