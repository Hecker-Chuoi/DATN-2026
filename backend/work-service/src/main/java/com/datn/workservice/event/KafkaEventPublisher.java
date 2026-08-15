package com.datn.workservice.event;

import com.datn.workservice.config.KafkaProducerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTaskAssigned(TaskAssignedEvent event) {
        String key = event.getTaskId().toString();
        log.info("Publishing TaskAssignedEvent to Kafka topic '{}': taskId={}, assigneeId={}",
                KafkaProducerConfig.TASK_EVENTS_TOPIC, event.getTaskId(), event.getAssigneeId());
        kafkaTemplate.send(KafkaProducerConfig.TASK_EVENTS_TOPIC, key, event);
    }

    public void publishTaskStatusChanged(TaskStatusChangedEvent event) {
        String key = event.getTaskId().toString();
        log.info("Publishing TaskStatusChangedEvent to Kafka topic '{}': taskId={}, status={}",
                KafkaProducerConfig.TASK_EVENTS_TOPIC, event.getTaskId(), event.getNewStatus());
        kafkaTemplate.send(KafkaProducerConfig.TASK_EVENTS_TOPIC, key, event);
    }

    public void publishCommentCreated(CommentCreatedEvent event) {
        String key = event.getTaskId().toString();
        log.info("Publishing CommentCreatedEvent to Kafka topic '{}': commentId={}, taskId={}",
                KafkaProducerConfig.COMMENT_EVENTS_TOPIC, event.getCommentId(), event.getTaskId());
        kafkaTemplate.send(KafkaProducerConfig.COMMENT_EVENTS_TOPIC, key, event);
    }
}
