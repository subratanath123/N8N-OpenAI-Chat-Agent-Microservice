package net.ai.chatbot.config;

import net.ai.chatbot.entity.WebsiteTrainEvent;
import net.ai.chatbot.service.pinnecone.PineconeService;
import net.ai.chatbot.service.redis.RedisConsumerGroupService;
import net.ai.chatbot.service.redis.RedisWebsiteCrawlMessageProcessor;
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

import static net.ai.chatbot.constants.Constants.REDIS_STREAM_SERVER_GROUP;
import static net.ai.chatbot.constants.Constants.TRAIN_WEBSITE_EVENT_STREAM;

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

    private final RedisConsumerGroupService redisConsumerGroupService;

    public RedisConfig(RedisConsumerGroupService redisConsumerGroupService) {
        this.redisConsumerGroupService = redisConsumerGroupService;
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

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, ObjectRecord<String, WebsiteTrainEvent>> streamMessageListenerContainer(RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, WebsiteTrainEvent>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .targetType(WebsiteTrainEvent.class)
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    public Subscription websiteTrainStreamSubscription(
            StreamMessageListenerContainer<String, ObjectRecord<String, WebsiteTrainEvent>> container,
            PineconeService pineconeService,
            MongoTemplate mongoTemplate
    ) throws UnknownHostException {

        redisConsumerGroupService.createConsumerGroupIfNotExists(
                redisConnectionFactory(), TRAIN_WEBSITE_EVENT_STREAM, REDIS_STREAM_SERVER_GROUP);

        StreamOffset<String> streamOffset = StreamOffset.create(TRAIN_WEBSITE_EVENT_STREAM, ReadOffset.lastConsumed());

        return container.receive(
                Consumer.from(REDIS_STREAM_SERVER_GROUP, InetAddress.getLocalHost().getHostName()),
                streamOffset,
                purchaseStreamListener(pineconeService, mongoTemplate)
        );
    }

    @Bean
    public StreamListener<String, ObjectRecord<String, WebsiteTrainEvent>> purchaseStreamListener(PineconeService pineconeService,
                                                                                                  MongoTemplate mongoTemplate) {
        // handle message from stream
        return new RedisWebsiteCrawlMessageProcessor(pineconeService, mongoTemplate);
    }
}