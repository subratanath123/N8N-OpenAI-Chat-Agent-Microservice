package net.ai.chatbot.config;

import net.ai.chatbot.service.mongodb.MongodbVectorService;
import net.ai.chatbot.service.n8n.N8nWebhookService;
import net.ai.chatbot.service.redis.KnowledgebaseProcessor;
import net.ai.chatbot.service.redis.RedisConsumerGroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import static net.ai.chatbot.constants.Constants.CHAT_BOT_CREATE_EVENT_STREAM;
import static net.ai.chatbot.constants.Constants.REDIS_STREAM_SERVER_GROUP;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username}")
    private String redisUsername;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${n8n.webhook.knowledgebase.train.url}")
    private String webhookKnowledgebaseTrainUrl;

    private final RedisConsumerGroupService redisConsumerGroupService;

    public RedisConfig(RedisConsumerGroupService redisConsumerGroupService) {
        this.redisConsumerGroupService = redisConsumerGroupService;
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        config.setUsername(redisUsername);
        config.setPassword(redisPassword);

        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisObjectTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());

        // Configure serializers (e.g., JSON serializer)
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(MapRecord.class));
        return template;
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamMessageListenerContainer(RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .targetType(String.class)
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    public Subscription websiteTrainStreamSubscription(
            StreamMessageListenerContainer<String, ObjectRecord<String, String>> container,
            MongoTemplate mongoTemplate,
            MongodbVectorService mongodbVectorService,
            N8nWebhookService n8nWebhookService
    ) throws UnknownHostException {

        redisConsumerGroupService
                .createConsumerGroupIfNotExists(redisConnectionFactory(), CHAT_BOT_CREATE_EVENT_STREAM, REDIS_STREAM_SERVER_GROUP);

        StreamOffset<String> streamOffset = StreamOffset.create(CHAT_BOT_CREATE_EVENT_STREAM, ReadOffset.lastConsumed());

        container.start();

        return container.receive(
                Consumer.from(REDIS_STREAM_SERVER_GROUP, InetAddress.getLocalHost().getHostName()),
                streamOffset,
                purchaseStreamListener(mongoTemplate, mongodbVectorService, n8nWebhookService)
        );
    }

    @Bean
    public StreamListener<String, ObjectRecord<String, String>> purchaseStreamListener(MongoTemplate mongoTemplate,
                                                                                       MongodbVectorService mongodbVectorService,
                                                                                       N8nWebhookService n8nWebhookService) {
        return new KnowledgebaseProcessor(mongoTemplate, mongodbVectorService, n8nWebhookService, webhookKnowledgebaseTrainUrl);
    }
}