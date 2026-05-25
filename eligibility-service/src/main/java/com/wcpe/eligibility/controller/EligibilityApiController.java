package com.wcpe.eligibility.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.service.EligibilityApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class EligibilityApiController {
    private final EligibilityApplicationService service;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EligibilityApiController(EligibilityApplicationService service, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/evaluate")
    ResponseEntity<EligibilityResult> evaluate(
            @PathVariable UUID tenantId,
            @RequestBody EligibilityRequest request,
            HttpServletRequest http) {

        // Idempotency check
        String idempotencyKey = http.getHeader("Idempotency-Key");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String requestHash = Hashing.sha256(objectMapper.writeValueAsString(request));

            // Try to find existing record
            try {
                String existingResponse = jdbcTemplate.queryForObject(
                    "SELECT response_json::text FROM eligibility.idempotency_record WHERE tenant_id = ? AND idempotency_key = ?",
                    String.class, tenantId, idempotencyKey
                );
                if (existingResponse != null) {
                    String existingHash = jdbcTemplate.queryForObject(
                        "SELECT request_hash FROM eligibility.idempotency_record WHERE tenant_id = ? AND idempotency_key = ?",
                        String.class, tenantId, idempotencyKey
                    );
                    if (!existingHash.equals(requestHash)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(null);
                    }
                    // Return cached result
                    EligibilityResult cached = objectMapper.readValue(existingResponse, EligibilityResult.class);
                    return ResponseEntity.ok(cached);
                }
            } catch (Exception e) {
                // No existing record, proceed with evaluation
            }
        }

        EligibilityResult result = service.evaluate(tenantId, request);

        // Store idempotency record if key was provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String responseJson = objectMapper.writeValueAsString(result);
                String requestHash = Hashing.sha256(objectMapper.writeValueAsString(request));
                jdbcTemplate.update(
                    "INSERT INTO eligibility.idempotency_record (tenant_id, idempotency_key, request_hash, response_type, response_json) " +
                    "VALUES (?, ?, ?, ?, ?::jsonb) " +
                    "ON CONFLICT (tenant_id, idempotency_key) DO NOTHING",
                    tenantId, idempotencyKey, requestHash, "EligibilityResult", responseJson
                );
            } catch (Exception e) {
                // Log and continue - idempotency storage is non-critical
            }
        }

        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", ex.getMessage()));
    }
}
