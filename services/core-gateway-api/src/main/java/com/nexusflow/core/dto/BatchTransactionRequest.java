package com.nexusflow.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransactionRequest {

    @NotEmpty(message = "Transactions list cannot be empty")
    @Size(max = 1000, message = "Maximum batch chunk size is 1000 items")
    @Valid
    private List<TransactionRequest> transactions;
}
