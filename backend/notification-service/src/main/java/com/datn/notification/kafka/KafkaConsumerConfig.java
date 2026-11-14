package com.datn.notification.kafka;

import com.datn.common.notification.NotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Xử lý lỗi khi đọc topic notification-events: thử lại 3 lần, cách nhau 2 giây; vẫn lỗi thì chuyển sang
 * {@code notification-events.DLT} để xem lại, không chặn các sự kiện phía sau.
 * JSON hỏng thì không thử lại (thử lại cũng không khác).
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    public static final String DLT = NotificationEvent.TOPIC + ".DLT";

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(NotificationEvent.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic notificationEventsDltTopic() {
        return TopicBuilder.name(DLT).partitions(3).replicas(1).build();
    }

    @Bean
    public CommonErrorHandler notificationErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        // Chỉ định rõ topic đích: spring-kafka 3.3 mặc định dùng hậu tố "-dlt", không phải ".DLT".
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate, KafkaConsumerConfig::dltDestination);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }

    /** Cùng số partition với bản ghi gốc trên notification-events.DLT. */
    static TopicPartition dltDestination(ConsumerRecord<?, ?> record, Exception ex) {
        return new TopicPartition(DLT, record.partition());
    }
}
