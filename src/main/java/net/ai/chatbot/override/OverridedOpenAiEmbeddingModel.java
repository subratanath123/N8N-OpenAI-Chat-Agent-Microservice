package net.ai.chatbot.override;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;

public class OverridedOpenAiEmbeddingModel extends OpenAiEmbeddingModel {
    public OverridedOpenAiEmbeddingModel(OpenAiApi openAiApi) {
        super(openAiApi);
    }

    public OverridedOpenAiEmbeddingModel(OpenAiApi openAiApi, MetadataMode metadataMode) {
        super(openAiApi, metadataMode);
    }

    public OverridedOpenAiEmbeddingModel(OpenAiApi openAiApi, MetadataMode metadataMode, OpenAiEmbeddingOptions openAiEmbeddingOptions) {
        super(openAiApi, metadataMode, openAiEmbeddingOptions);
    }

    public OverridedOpenAiEmbeddingModel(OpenAiApi openAiApi, MetadataMode metadataMode, OpenAiEmbeddingOptions options, RetryTemplate retryTemplate) {
        super(openAiApi, metadataMode, options, retryTemplate);
    }

    public OverridedOpenAiEmbeddingModel(OpenAiApi openAiApi, MetadataMode metadataMode, OpenAiEmbeddingOptions options, RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
        super(openAiApi, metadataMode, options, retryTemplate, observationRegistry);
    }

}
