package net.ai.chatbot.filter;

import jakarta.servlet.*;
import net.ai.chatbot.utils.AuthUtils;

import java.io.IOException;

public class ThreadLocalVectorStoreFilter implements Filter {

//    private final EmbeddingModel embeddingModel;
//    private final PineconeConfig pineconeConfig;
//    private final ThreadLocalVectorStoreHolder threadLocalVectorStoreHolder;

//    public ThreadLocalVectorStoreFilter(ThreadLocalVectorStoreHolder threadLocalVectorStoreHolder,
//                                        EmbeddingModel embeddingModel,
//                                        PineconeConfig pineconeConfig) {
//
//        this.threadLocalVectorStoreHolder = threadLocalVectorStoreHolder;
//        this.embeddingModel = embeddingModel;
//        this.pineconeConfig = pineconeConfig;
//    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String email = AuthUtils.getEmail();
//
//        if (email != null && !email.isEmpty()) {
//            threadLocalVectorStoreHolder.set(pineconeConfig.pineconeVectorStore(embeddingModel));
//        }

        try {
            chain.doFilter(request, response);

        } finally {
//            threadLocalVectorStoreHolder.clear();
        }
    }
}
