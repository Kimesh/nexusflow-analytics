package com.nexusflow.processor.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusflow.processor.service.CqrsProjectionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final CqrsProjectionService projectionService;
    private final StringRedisTemplate redisTemplate;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${aws.sqs.dlq-url:http://localhost:4566/000000000000/nexusflow-dlq}")
    private String dlqUrl;

    private Counter processedCounter;
    private Counter duplicateCounter;

    @PostConstruct
    public void initMetrics() {
        this.processedCounter = meterRegistry.counter("nexusflow_events_processed_total");
        this.duplicateCounter = meterRegistry.counter("nexusflow_events_duplicates_total");
    }

    @KafkaListener(topics = "transaction.events", groupId = "nexusflow-cqrs-group")
    public void consumeTransactionEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String txId = root.path("id").asText();

            if (txId == null || txId.isBlank()) {
                log.warn("Received malformed event missing transaction ID: {}", payload);
                sendToDlq(payload, "Malformed: missing id");
                return;
            }

            // Distributed Idempotency Deduplication Check
            String dedupKey = "consumer:dedup:" + txId;
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "PROCESSED", Duration.ofHours(48));

            if (Boolean.FALSE.equals(isNew)) {
                log.info("Duplicate event detected for txId {}. Skipping projection to maintain idempotency.", txId);
                duplicateCounter.increment();
                return;
            }

            // Project denormalized CQRS read model
            projectionService.projectTransaction(payload);
            processedCounter.increment();

        } catch (Exception ex) {
            log.error("Fatal error processing Kafka event: {}. Routing to DLQ.", ex.getMessage(), ex);
            sendToDlq(payload, ex.getMessage());
        }
    }

    private void sendToDlq(String payload, String reason) {
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .messageBody(payload)
                    .build());
            log.warn("Routed poisoned message to SQS DLQ: {}", reason);
        } catch (Exception e) {
            log.error("Failed to forward poisoned message to SQS DLQ: {}", e.getMessage());
        }
    }
}
