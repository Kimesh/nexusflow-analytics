package com.nexusflow.processor.controller;

import com.nexusflow.processor.service.BatchReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reconcile")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReconciliationController {

    private final BatchReconciliationService reconciliationService;

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerReconciliation(
            @RequestParam(defaultValue = "10000") int totalRecords,
            @RequestParam(defaultValue = "1000") int chunkSize) {
        Map<String, Object> result = reconciliationService.executeReconciliation(totalRecords, chunkSize);
        return ResponseEntity.ok(result);
    }
}
