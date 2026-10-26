package com.nexusflow.core.service;

import com.nexusflow.core.model.OutboxEvent;
import com.nexusflow.core.model.OutboxStatus;
import com.nexusflow.core.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public static final String TOPIC_TRANSACTIONS = "transaction.events";
    private final AtomicLong pendingGauge = new AtomicLong(0);

    @PostConstruct
    public void initMetrics() {
        meterRegistry.gauge("nexusflow_outbox_pending_events", pendingGauge);
    }

    @Scheduled(fixedDelayString = "${nexusflow.outbox.polling-rate-ms:500}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(
                OutboxStatus.PENDING,
                PageRequest.of(0, 100)
        );

        long pendingTotal = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        pendingGauge.set(pendingTotal);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Outbox relay processing batch of {} events. Total pending: {}", pendingEvents.size(), pendingTotal);

        for (OutboxEvent event : pendingEvents) {
            try {
                // Key by AggregateId (sender or transaction id) to ensure partition-level order in Kafka
                kafkaTemplate.send(TOPIC_TRANSACTIONS, event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                event.setStatus(OutboxStatus.PUBLISHED);
                                event.setProcessedAt(Instant.now());
                                outboxEventRepository.save(event);
                                log.debug("Published outbox event {} to Kafka topic {}", event.getId(), TOPIC_TRANSACTIONS);
                            } else {
                                handleFailure(event, ex);
                            }
                        });
            } catch (Exception ex) {
                handleFailure(event, ex);
            }
        }
    }

    private void handleFailure(OutboxEvent event, Throwable ex) {
        log.error("Failed to publish outbox event {} to Kafka: {}", event.getId(), ex.getMessage());
        event.setRetryCount(event.getRetryCount() + 1);
        event.setErrorMessage(ex.getMessage());
        if (event.getRetryCount() >= 5) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event {} exceeded maximum retries. Flagged as FAILED for manual operator review.", event.getId());
        }
        outboxEventRepository.save(event);
    }
}
