package com.nexusflow.core.dto;

import com.nexusflow.core.model.Transaction;
import com.nexusflow.core.model.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String id;
    private String idempotencyKey;
    private String senderAccountId;
    private String recipientAccountId;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private String transactionType;
    private Instant createdAt;

    public static TransactionResponse fromEntity(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .idempotencyKey(tx.getIdempotencyKey())
                .senderAccountId(tx.getSenderAccountId())
                .recipientAccountId(tx.getRecipientAccountId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus())
                .transactionType(tx.getTransactionType())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
