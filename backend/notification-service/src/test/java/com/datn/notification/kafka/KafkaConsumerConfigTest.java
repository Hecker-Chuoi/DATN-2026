package com.datn.notification.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    @Test
    void failedRecords_goToDotDltTopic_onSamePartition() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events", 2, 41L, "k", "{ hỏng");
        TopicPartition tp = KafkaConsumerConfig.dltDestination(record, new RuntimeException("x"));
        assertThat(tp.topic()).isEqualTo("notification-events.DLT");
        assertThat(tp.partition()).isEqualTo(2);
    }
}
