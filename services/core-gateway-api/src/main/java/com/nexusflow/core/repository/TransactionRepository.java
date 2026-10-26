package com.nexusflow.core.repository;

import com.nexusflow.core.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
