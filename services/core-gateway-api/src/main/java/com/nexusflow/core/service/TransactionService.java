package com.nexusflow.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusflow.core.dto.BatchTransactionRequest;
import com.nexusflow.core.dto.BatchTransactionResponse;
import com.nexusflow.core.dto.TransactionRequest;
import com.nexusflow.core.dto.TransactionResponse;
import com.nexusflow.core.model.OutboxEvent;
import com.nexusflow.core.model.OutboxStatus;
import com.nexusflow.core.model.Transaction;
import com.nexusflow.core.model.TransactionStatus;
import com.nexusflow.core.repository.OutboxEventRepository;
import com.nexusflow.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:tx:";

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request, String idempotencyKey) {
        // 1. Redis Distributed Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
            try {
                String cachedPayload = redisTemplate.opsForValue().get(redisKey);
                if (cachedPayload != null) {
                    if ("PROCESSING".equals(cachedPayload)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent request with identical idempotency key is currently processing.");
                    }
                    log.info("Idempotent hit from Redis cache for key: {}", idempotencyKey);
                    return objectMapper.readValue(cachedPayload, TransactionResponse.class);
                }

                // Acquire lock flag for in-flight protection
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(30));
                if (Boolean.FALSE.equals(acquired)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate in-flight request detected.");
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cached response for idempotency key: {}", idempotencyKey);
            } catch (ResponseStatusException rse) {
                throw rse;
            } catch (Exception ex) {
                log.warn("Redis idempotency check warning: {}", ex.getMessage());
            }

            // Database secondary idempotency verification
            var existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existingTx.isPresent()) {
                return TransactionResponse.fromEntity(existingTx.get());
            }
        }

        // 2. Build Transaction Entity (Write Model)
        String transactionId = "TX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        Transaction transaction = Transaction.builder()
                .id(transactionId)
                .idempotencyKey(idempotencyKey)
                .senderAccountId(request.getSenderAccountId())
                .recipientAccountId(request.getRecipientAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .status(TransactionStatus.SETTLED)
                .transactionType(request.getTransactionType() != null ? request.getTransactionType() : "PAYMENT")
                .metadata(request.getMetadata())
                .build();

        Transaction savedTx = transactionRepository.save(transaction);

        // 3. Build & Persist Transactional Outbox Event (Atomic Dual Write Guarantee)
        try {
            String eventPayload = objectMapper.writeValueAsString(savedTx);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("TRANSACTION")
                    .aggregateId(savedTx.getId())
                    .eventType("TRANSACTION_CREATED")
                    .payload(eventPayload)
                    .status(OutboxStatus.PENDING)
                    .build();

            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize outbox event payload for transaction: {}", savedTx.getId(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Event serialization failure");
        }

        TransactionResponse response = TransactionResponse.fromEntity(savedTx);

        // 4. Update Redis Idempotency Record with Completed Response
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String serializedResponse = objectMapper.writeValueAsString(response);
                redisTemplate.opsForValue().set(IDEMPOTENCY_PREFIX + idempotencyKey, serializedResponse, Duration.ofHours(24));
            } catch (Exception ex) {
                log.warn("Failed to store completed idempotency payload in Redis: {}", ex.getMessage());
            }
        }

        log.debug("Transaction {} successfully committed and queued to outbox", savedTx.getId());
        return response;
    }

    @Transactional
    public BatchTransactionResponse processBatch(BatchTransactionRequest batchRequest) {
        long startTime = System.currentTimeMillis();
        List<String> ids = new ArrayList<>(batchRequest.getTransactions().size());
        List<Transaction> transactionsToSave = new ArrayList<>(batchRequest.getTransactions().size());
        List<OutboxEvent> outboxEventsToSave = new ArrayList<>(batchRequest.getTransactions().size());

        for (TransactionRequest req : batchRequest.getTransactions()) {
            String txId = "TX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            Transaction tx = Transaction.builder()
                    .id(txId)
                    .senderAccountId(req.getSenderAccountId())
                    .recipientAccountId(req.getRecipientAccountId())
                    .amount(req.getAmount())
                    .currency(req.getCurrency() != null ? req.getCurrency() : "USD")
                    .status(TransactionStatus.SETTLED)
                    .transactionType(req.getTransactionType() != null ? req.getTransactionType() : "BATCH_PAYMENT")
                    .metadata(req.getMetadata())
                    .build();

            transactionsToSave.add(tx);
            ids.add(txId);

            try {
                String payload = objectMapper.writeValueAsString(tx);
                outboxEventsToSave.add(OutboxEvent.builder()
                        .aggregateType("TRANSACTION")
                        .aggregateId(tx.getId())
                        .eventType("TRANSACTION_CREATED")
                        .payload(payload)
                        .status(OutboxStatus.PENDING)
                        .build());
            } catch (Exception e) {
                log.error("Batch serialization error for tx: {}", txId, e);
            }
        }

        transactionRepository.saveAll(transactionsToSave);
        outboxEventRepository.saveAll(outboxEventsToSave);

        long executionTime = System.currentTimeMillis() - startTime;
        log.info("Batch processed: {} records committed in {}ms", transactionsToSave.size(), executionTime);

        return BatchTransactionResponse.builder()
                .totalSubmitted(batchRequest.getTransactions().size())
                .successfullyIngested(transactionsToSave.size())
                .failed(batchRequest.getTransactions().size() - transactionsToSave.size())
                .transactionIds(ids)
                .executionTimeMs(executionTime)
                .processedAt(Instant.now())
                .build();
    }

    public TransactionResponse getTransaction(String id) {
        return transactionRepository.findById(id)
                .map(TransactionResponse::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));
    }
}
