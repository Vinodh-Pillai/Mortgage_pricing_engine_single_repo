package com.wcpe.auditreplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.AuditRecorder;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditRecordControllerTest {

    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final AuditRecordRepository repository = mock(AuditRecordRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new AuditRecordController(auditRecorder, repository, objectMapper))
            .setControllerAdvice(new AuditProblemDetailsAdvice())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    @Test
    void createsInternalTenantScopedAuditRecord() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuditRecord record = record(tenantId);
        when(auditRecorder.record(any())).thenReturn(record);

        mockMvc.perform(post("/internal/v1/tenants/{tenantId}/audit-records", tenantId)
                        .header("Authorization", "Bearer service-token")
                        .header("Idempotency-Key", "idem-1")
                        .contentType("application/json")
                        .content(requestBody(record)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.action").value("QUOTE_CREATED"))
                .andExpect(jsonPath("$.snapshotJson.borrower").value("REDACTED"))
                .andExpect(jsonPath("$.integrityHash").value("sha-256-fixture"));
    }

    @Test
    void readsWithoutCrossTenantLeak() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuditRecord record = record(tenantId);
        when(repository.findByTenantIdAndId(tenantId, record.getId())).thenReturn(Optional.of(record));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records/{auditRecordId}", tenantId, record.getId())
                        .header("Authorization", "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectType").value("quote"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records/{auditRecordId}", UUID.randomUUID(), record.getId())
                        .header("Authorization", "Bearer read-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsProblemDetailForValidationErrors() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuditRecord record = record(tenantId);

        mockMvc.perform(post("/internal/v1/tenants/{tenantId}/audit-records", tenantId)
                        .header("Authorization", "Bearer service-token")
                        .contentType("application/json")
                        .content(requestBody(record)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key is required"))
                .andExpect(jsonPath("$.path").value("/internal/v1/tenants/" + tenantId + "/audit-records"));
    }

    @Test
    void rejectsMissingAuthorizationWithProblemDetail() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records/{auditRecordId}", tenantId, UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Authorization header is required"));
    }

    @Test
    void returnsProblemDetailWithoutCrossTenantLeak() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID auditRecordId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records/{auditRecordId}", tenantId, auditRecordId)
                        .header("Authorization", "Bearer read-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Audit record not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/tenants/" + tenantId + "/audit-records/" + auditRecordId));
    }

    private String requestBody(AuditRecord record) {
        return """
                {
                  "action": "QUOTE_CREATED",
                  "subjectType": "quote",
                  "subjectId": "quote-123",
                  "subjectVersion": 1,
                  "actorType": "SERVICE",
                  "actorId": "quote-service",
                  "actorDisplay": "Quote Service",
                  "correlationId": "%s",
                  "causationId": "%s",
                  "requestId": "%s",
                  "sourceIpHash": "source-ip-hash",
                  "userAgentHash": "user-agent-hash",
                  "beforeSnapshotJson": {"before":"encrypted"},
                  "afterSnapshotJson": {"after":"encrypted"},
                  "snapshotJson": {"borrower":"REDACTED"},
                  "snapshotEncryptionKeyRef": "kms://tenant/audit-key-ref",
                  "redactionProfile": "mortgage-pricing-default-v1",
                  "configVersionRefs": {"retentionPolicy":"tenant-config-ref"},
                  "result": "SUCCESS",
                  "occurredAt": "%s",
                  "retentionUntil": "%s",
                  "legalHold": false
                }
                """.formatted(record.getCorrelationId(), record.getCausationId(), record.getRequestId(), record.getOccurredAt(), record.getRetentionUntil());
    }

    private AuditRecord record(UUID tenantId) {
        return AuditRecord.create(
                tenantId,
                UUID.randomUUID(),
                "QUOTE_CREATED",
                "quote",
                "quote-123",
                1L,
                "SERVICE",
                "quote-service",
                "Quote Service",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "source-ip-hash",
                "user-agent-hash",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"borrower\":\"REDACTED\"}".getBytes(StandardCharsets.UTF_8),
                "mortgage-pricing-default-v1",
                "{\"retentionPolicy\":\"tenant-config-ref\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                Instant.now(),
                LocalDate.now().plusYears(7),
                false,
                "sha-256-fixture",
                null);
    }
}
