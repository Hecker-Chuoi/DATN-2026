package com.datn.workservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    public static final String TASK_EVENTS_TOPIC = "task-events-topic";
    public static final String COMMENT_EVENTS_TOPIC = "comment-events-topic";
    public static final String PROJECT_EVENTS_TOPIC = "project-events-topic";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        // OutboxRelay chạy mỗi giây: Kafka sập thì phải trả lỗi nhanh (mặc định chặn tới 60 giây)
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 9_000);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /** Sự kiện thông báo, notification-service tiêu thụ. Key = projectId. */
    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(com.datn.common.notification.NotificationEvent.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public NewTopic taskEventsTopic() {
        return TopicBuilder.name(TASK_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic commentEventsTopic() {
        return TopicBuilder.name(COMMENT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic projectEventsTopic() {
        return TopicBuilder.name(PROJECT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
