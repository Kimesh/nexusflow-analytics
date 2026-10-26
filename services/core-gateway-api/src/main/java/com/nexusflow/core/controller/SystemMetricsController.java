package com.nexusflow.core.controller;

import com.nexusflow.core.model.OutboxStatus;
import com.nexusflow.core.repository.OutboxEventRepository;
import com.nexusflow.core.repository.TransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "System", description = "Operational Metrics & Cluster Telemetry")
public class SystemMetricsController {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;

    @GetMapping("/overview")
    @Operation(summary = "System Overview", description = "Retrieves high-level counts of ingested transactions and outbox state.")
    public ResponseEntity<Map<String, Object>> getSystemOverview() {
        long totalTransactions = transactionRepository.count();
        long pendingOutbox = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long publishedOutbox = outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED);
        long failedOutbox = outboxEventRepository.countByStatus(OutboxStatus.FAILED);

        return ResponseEntity.ok(Map.of(
                "status", "HEALTHY",
                "totalTransactions", totalTransactions,
                "outboxPending", pendingOutbox,
                "outboxPublished", publishedOutbox,
                "outboxFailed", failedOutbox,
                "timestamp", Instant.now()
        ));
    }
}
