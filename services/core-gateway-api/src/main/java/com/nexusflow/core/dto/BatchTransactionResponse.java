package com.nexusflow.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransactionResponse {

    private int totalSubmitted;
    private int successfullyIngested;
    private int failed;
    private List<String> transactionIds;
    private long executionTimeMs;
    private Instant processedAt;
}
