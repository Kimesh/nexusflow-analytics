package com.nexusflow.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CqrsProjectionService {

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.dynamodb.table-name:nexusflow_events}")
    private String tableName;

    /**
     * Projects transaction into DynamoDB Single-Table read models:
     * 1. Transaction metadata entity (PK: TX#<id>, SK: METADATA)
     * 2. Sender timeline entity (PK: ACCT#<senderId>, SK: TX#<timestamp>#<id>)
     * 3. Recipient timeline entity (PK: ACCT#<recipientId>, SK: TX#<timestamp>#<id>)
     */
    public void projectTransaction(String payloadJson) {
        try {
            JsonNode tx = objectMapper.readTree(payloadJson);
            String txId = tx.path("id").asText();
            String senderId = tx.path("senderAccountId").asText();
            String recipientId = tx.path("recipientAccountId").asText();
            String amount = tx.path("amount").asText();
            String currency = tx.path("currency").asText("USD");
            String status = tx.path("status").asText("PENDING");
            String createdAt = tx.path("createdAt").asText();
            if (createdAt == null || createdAt.isBlank()) {
                createdAt = String.valueOf(System.currentTimeMillis());
            }

            // 1. Transaction Item
            Map<String, AttributeValue> txItem = new HashMap<>();
            txItem.put("PK", AttributeValue.builder().s("TX#" + txId).build());
            txItem.put("SK", AttributeValue.builder().s("METADATA").build());
            txItem.put("GSI1PK", AttributeValue.builder().s("STATUS#" + status).build());
            txItem.put("GSI1SK", AttributeValue.builder().s(createdAt).build());
            txItem.put("TransactionId", AttributeValue.builder().s(txId).build());
            txItem.put("SenderAccountId", AttributeValue.builder().s(senderId).build());
            txItem.put("RecipientAccountId", AttributeValue.builder().s(recipientId).build());
            txItem.put("Amount", AttributeValue.builder().n(amount).build());
            txItem.put("Currency", AttributeValue.builder().s(currency).build());
            txItem.put("Status", AttributeValue.builder().s(status).build());
            txItem.put("CreatedAt", AttributeValue.builder().s(createdAt).build());

            // 2. Sender Timeline Item (DEBIT)
            Map<String, AttributeValue> senderItem = new HashMap<>();
            senderItem.put("PK", AttributeValue.builder().s("ACCT#" + senderId).build());
            senderItem.put("SK", AttributeValue.builder().s("TX#" + createdAt + "#" + txId).build());
            senderItem.put("GSI1PK", AttributeValue.builder().s("STATUS#" + status).build());
            senderItem.put("GSI1SK", AttributeValue.builder().s(createdAt).build());
            senderItem.put("Type", AttributeValue.builder().s("DEBIT").build());
            senderItem.put("Counterparty", AttributeValue.builder().s(recipientId).build());
            senderItem.put("Amount", AttributeValue.builder().n(amount).build());
            senderItem.put("Currency", AttributeValue.builder().s(currency).build());

            // 3. Recipient Timeline Item (CREDIT)
            Map<String, AttributeValue> recipientItem = new HashMap<>();
            recipientItem.put("PK", AttributeValue.builder().s("ACCT#" + recipientId).build());
            recipientItem.put("SK", AttributeValue.builder().s("TX#" + createdAt + "#" + txId).build());
            recipientItem.put("GSI1PK", AttributeValue.builder().s("STATUS#" + status).build());
            recipientItem.put("GSI1SK", AttributeValue.builder().s(createdAt).build());
            recipientItem.put("Type", AttributeValue.builder().s("CREDIT").build());
            recipientItem.put("Counterparty", AttributeValue.builder().s(senderId).build());
            recipientItem.put("Amount", AttributeValue.builder().n(amount).build());
            recipientItem.put("Currency", AttributeValue.builder().s(currency).build());

            // Write all items
            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(txItem).build());
            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(senderItem).build());
            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(recipientItem).build());

            log.info("Projected transaction {} into DynamoDB CQRS read models", txId);
        } catch (Exception ex) {
            log.error("Failed to project transaction into DynamoDB: {}", ex.getMessage(), ex);
            throw new RuntimeException("CQRS projection failure", ex);
        }
    }

    public List<Map<String, String>> getAccountTimeline(String accountId) {
        String pk = "ACCT#" + accountId;

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s(pk).build(),
                        ":skPrefix", AttributeValue.builder().s("TX#").build()
                ))
                .scanIndexForward(false) // Newest first
                .limit(50)
                .build();

        try {
            QueryResponse response = dynamoDbClient.query(queryRequest);
            List<Map<String, String>> results = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                Map<String, String> mapped = new HashMap<>();
                item.forEach((k, v) -> mapped.put(k, v.s() != null ? v.s() : (v.n() != null ? v.n() : v.toString())));
                results.add(mapped);
            }
            return results;
        } catch (Exception ex) {
            log.warn("DynamoDB query failed or table not ready: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, String> getTransactionDetails(String txId) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.builder().s("TX#" + txId).build(),
                        "SK", AttributeValue.builder().s("METADATA").build()
                ))
                .build();

        try {
            GetItemResponse response = dynamoDbClient.getItem(request);
            if (!response.hasItem()) {
                return Collections.emptyMap();
            }
            Map<String, String> mapped = new HashMap<>();
            response.item().forEach((k, v) -> mapped.put(k, v.s() != null ? v.s() : (v.n() != null ? v.n() : v.toString())));
            return mapped;
        } catch (Exception ex) {
            log.warn("DynamoDB get item error: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }
}
