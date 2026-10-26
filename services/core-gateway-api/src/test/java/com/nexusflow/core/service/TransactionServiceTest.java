package com.nexusflow.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusflow.core.dto.TransactionRequest;
import com.nexusflow.core.dto.TransactionResponse;
import com.nexusflow.core.model.OutboxEvent;
import com.nexusflow.core.model.Transaction;
import com.nexusflow.core.model.TransactionStatus;
import com.nexusflow.core.repository.OutboxEventRepository;
import com.nexusflow.core.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        transactionService = new TransactionService(transactionRepository, outboxEventRepository, redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("Should successfully persist transaction and transactional outbox event atomically")
    void testProcessTransaction_Success() {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountId("ACCT_SENDER_100")
                .recipientAccountId("ACCT_RECIPIENT_200")
                .amount(new BigDecimal("1500.50"))
                .currency("USD")
                .build();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            return tx;
        });

        TransactionResponse response = transactionService.processTransaction(request, "IDEMP_KEY_001");

        assertThat(response).isNotNull();
        assertThat(response.getSenderAccountId()).isEqualTo("ACCT_SENDER_100");
        assertThat(response.getRecipientAccountId()).isEqualTo("ACCT_RECIPIENT_200");
        assertThat(response.getAmount()).isEqualTo(new BigDecimal("1500.50"));
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SETTLED);

        // Verify outbox was written
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should return cached response on idempotent duplicate submission")
    void testProcessTransaction_IdempotentHit() throws Exception {
        TransactionResponse existing = TransactionResponse.builder()
                .id("TX_CACHED_123")
                .senderAccountId("ACCT_SENDER_100")
                .recipientAccountId("ACCT_RECIPIENT_200")
                .amount(new BigDecimal("250.00"))
                .status(TransactionStatus.SETTLED)
                .build();

        String cachedJson = objectMapper.writeValueAsString(existing);
        when(valueOperations.get("idempotency:tx:KEY_DUPLICATE")).thenReturn(cachedJson);

        TransactionRequest request = TransactionRequest.builder()
                .senderAccountId("ACCT_SENDER_100")
                .recipientAccountId("ACCT_RECIPIENT_200")
                .amount(new BigDecimal("250.00"))
                .build();

        TransactionResponse response = transactionService.processTransaction(request, "KEY_DUPLICATE");

        assertThat(response.getId()).isEqualTo("TX_CACHED_123");
        // Verify DB was bypassed
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
