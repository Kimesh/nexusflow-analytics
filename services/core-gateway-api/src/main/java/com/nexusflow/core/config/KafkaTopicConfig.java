package com.nexusflow.core.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TRANSACTION_TOPIC = "transaction.events";
    public static final String DLQ_TOPIC = "transaction.events.dlq";

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name(TRANSACTION_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name(DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
