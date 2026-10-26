package com.nexusflow.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_tx_sender", columnList = "senderAccountId"),
    @Index(name = "idx_tx_recipient", columnList = "recipientAccountId"),
    @Index(name = "idx_tx_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "sender_account_id", nullable = false, length = 64)
    private String senderAccountId;

    @Column(name = "recipient_account_id", nullable = false, length = 64)
    private String recipientAccountId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "transaction_type", nullable = false, length = 32)
    @Builder.Default
    private String transactionType = "PAYMENT";

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
