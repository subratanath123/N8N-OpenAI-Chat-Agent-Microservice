package net.ai.chatbot.service.pinnecone;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PineconeService {

    private final PineconeVectorStoreFactory factory;

    public PineconeService(PineconeVectorStoreFactory factory) {
        this.factory = factory;
    }

    /**
     * Stores text embeddings along with metadata in Pinecone.
     */
    public void storeDocument(String nameSpace, List<Document> documents) {
        factory.createForNamespace(nameSpace).add(documents);
    }

    /**
     * Searches for similar vectors using embeddings and metadata filtering.
     */
    public List<Document> search(String nameSpace, String query) {
        return factory.createForNamespace(nameSpace).similaritySearch(
                SearchRequest
                        .builder()
                        .query(query)
                        .topK(100)
                        .build()
        );
    }
}
