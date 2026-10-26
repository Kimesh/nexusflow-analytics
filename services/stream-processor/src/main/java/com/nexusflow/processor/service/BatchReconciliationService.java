package com.nexusflow.processor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchReconciliationService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_KEY = "lock:reconciliation:batch";
    private static final String LUA_RELEASE_LOCK =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    /**
     * Executes multithreaded batch reconciliation with Redis distributed lock protection.
     * Prevents split-brain execution across multiple worker pods.
     */
    public Map<String, Object> executeReconciliation(int totalRecords, int chunkSize) {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockToken, Duration.ofSeconds(60));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Batch reconciliation lock already held by another pod replica. Aborting concurrent run.");
            return Map.of(
                    "status", "SKIPPED",
                    "reason", "Lock held by another pod. Concurrency conflict avoided."
            );
        }

        long startTime = System.currentTimeMillis();
        AtomicInteger processedRecords = new AtomicInteger(0);
        AtomicInteger discrepanciesFound = new AtomicInteger(0);

        try {
            int totalChunks = (int) Math.ceil((double) totalRecords / chunkSize);
            log.info("Acquired distributed lock. Starting high-scale multithreaded batch reconciliation ({} records, {} chunks)",
                    totalRecords, totalChunks);

            int workerThreads = Math.min(Runtime.getRuntime().availableProcessors() * 2, 8);
            ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                final int currentChunk = chunkIndex;
                futures.add(CompletableFuture.runAsync(() -> {
                    int chunkStart = currentChunk * chunkSize;
                    int chunkEnd = Math.min(chunkStart + chunkSize, totalRecords);
                    int count = chunkEnd - chunkStart;

                    // Simulate complex balance ledger verification and integrity checksum
                    try {
                        Thread.sleep(15); // Simulated computation
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    processedRecords.addAndGet(count);
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch reconciliation completed: {} records verified in {}ms", processedRecords.get(), duration);

            return Map.of(
                    "status", "COMPLETED",
                    "recordsProcessed", processedRecords.get(),
                    "discrepancies", discrepanciesFound.get(),
                    "executionTimeMs", duration,
                    "workerThreads", workerThreads
            );

        } finally {
            // Safely release lock using atomic Lua script (verifying ownership token)
            try {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_RELEASE_LOCK, Long.class);
                redisTemplate.execute(script, Collections.singletonList(LOCK_KEY), lockToken);
                log.debug("Released batch reconciliation distributed lock.");
            } catch (Exception ex) {
                log.error("Failed to release distributed lock: {}", ex.getMessage());
            }
        }
    }
}
