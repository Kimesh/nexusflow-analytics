package com.nexusflow.core.controller;

import com.nexusflow.core.dto.BatchTransactionRequest;
import com.nexusflow.core.dto.BatchTransactionResponse;
import com.nexusflow.core.dto.TransactionRequest;
import com.nexusflow.core.dto.TransactionResponse;
import com.nexusflow.core.service.RateLimiterService;
import com.nexusflow.core.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transactions", description = "High-throughput Transaction Ingestion & Outbox Endpoints")
public class TransactionController {

    private final TransactionService transactionService;
    private final RateLimiterService rateLimiterService;

    @PostMapping
    @Operation(summary = "Submit a financial transaction", description = "Atomically persists transaction and queues to Outbox with distributed idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Transaction accepted and queued"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "409", description = "Concurrent idempotent submission"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<TransactionResponse> submitTransaction(
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(description = "Optional client UUID for atomic deduplication") String idempotencyKey,
            @Valid @RequestBody TransactionRequest request,
            HttpServletRequest servletRequest) {

        String clientIp = servletRequest.getRemoteAddr();
        if (!rateLimiterService.isAllowed(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please back off.");
        }

        TransactionResponse response = transactionService.processTransaction(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/batch")
    @Operation(summary = "Submit a batch of transactions", description = "High-scale batch ingestion supporting up to 1,000 transactions per chunk with parallel outbox dispatch.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Batch successfully processed"),
            @ApiResponse(responseCode = "400", description = "Batch validation error")
    })
    public ResponseEntity<BatchTransactionResponse> submitBatch(
            @Valid @RequestBody BatchTransactionRequest batchRequest) {
        BatchTransactionResponse response = transactionService.processBatch(batchRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID", description = "Retrieves transactional write-model state by ID.")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable String id) {
        return ResponseEntity.ok(transactionService.getTransaction(id));
    }
}
