package com.nexusflow.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotBlank(message = "senderAccountId is required")
    private String senderAccountId;

    @NotBlank(message = "recipientAccountId is required")
    private String recipientAccountId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "USD";

    @Builder.Default
    private String transactionType = "PAYMENT";

    private String metadata;
}
