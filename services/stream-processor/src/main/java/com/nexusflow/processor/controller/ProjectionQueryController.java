package com.nexusflow.processor.controller;

import com.nexusflow.processor.service.CqrsProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectionQueryController {

    private final CqrsProjectionService projectionService;

    @GetMapping("/account/{accountId}/timeline")
    public ResponseEntity<List<Map<String, String>>> getAccountTimeline(@PathVariable String accountId) {
        List<Map<String, String>> timeline = projectionService.getAccountTimeline(accountId);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/transaction/{txId}")
    public ResponseEntity<Map<String, String>> getTransaction(@PathVariable String txId) {
        Map<String, String> details = projectionService.getTransactionDetails(txId);
        if (details.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }
}
