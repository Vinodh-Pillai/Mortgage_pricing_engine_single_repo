package com.wcpe.auditreplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.AuditIntegrityHashService;
import com.wcpe.auditreplay.application.AuditRecorder;
import com.wcpe.auditreplay.application.AuditSearchService;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.filter.CorrelationIdFilter;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditRecordControllerTest {

    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final AuditRecordRepository repository = mock(AuditRecordRepository.class);
    private final AuditSearchService auditSearchService = mock(AuditSearchService.class);
    private final AuditIntegrityHashService auditIntegrityHashService = mock(AuditIntegrityHashService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new AuditRecordController(auditRecorder, repository, auditSearchService, auditIntegrityHashService, objectMapper))
            .addFilters(new CorrelationIdFilter())
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
                        .header("X-Correlation-Id", record.getCorrelationId().toString())
                        .contentType("application/json")
                        .content(requestBody(record)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", record.getCorrelationId().toString()))
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
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.subjectType").value("quote"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records/{auditRecordId}", UUID.randomUUID(), record.getId())
                        .header("Authorization", "Bearer read-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchesTenantAuditRecordsWithAllowlistedFilters() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-07T10:15:30Z");
        when(auditSearchService.search(any(), any())).thenReturn(new AuditSearchService.AuditSearchPage(
                List.of(new AuditSearchService.AuditSearchResult(
                        recordId,
                        occurredAt.toString(),
                        "QUOTE_CREATED",
                        "quote",
                        "quote-123",
                        "actor-hash",
                        "SUCCESS",
                        UUID.fromString("00000000-0000-0000-0000-000000000013"),
                        "VERIFIED",
                        false,
                        LocalDate.parse("2033-06-07"))),
                null,
                1,
                false));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records", tenantId)
                        .header("Authorization", "Bearer read-token")
                        .param("from", "2026-06-07T00:00:00Z")
                        .param("to", "2026-06-08T00:00:00Z")
                        .param("subjectType", "quote")
                        .param("result", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].auditRecordId").value(recordId.toString()))
                .andExpect(jsonPath("$.results[0].actorIdHash").value("actor-hash"))
                .andExpect(jsonPath("$.results[0].integrityStatus").value("VERIFIED"));
    }

    @Test
    void verifiesAuditIntegrityRange() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID firstBreakRecordId = UUID.randomUUID();
        Instant from = Instant.parse("2026-06-07T00:00:00Z");
        Instant to = Instant.parse("2026-06-08T00:00:00Z");
        when(auditIntegrityHashService.verifyTenantChain(tenantId, from, to))
                .thenReturn(new AuditIntegrityHashService.AuditIntegrityVerification(
                        tenantId,
                        from,
                        to,
                        10,
                        1,
                        firstBreakRecordId,
                        "BROKEN",
                        "SHA-256",
                        "audit-record-canonical-v1"));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/audit-integrity/verifications", tenantId)
                        .header("Authorization", "Bearer read-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "from": "2026-06-07T00:00:00Z",
                                  "to": "2026-06-08T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BROKEN"))
                .andExpect(jsonPath("$.checkedCount").value(10))
                .andExpect(jsonPath("$.brokenCount").value(1))
                .andExpect(jsonPath("$.firstBreakRecordId").value(firstBreakRecordId.toString()))
                .andExpect(jsonPath("$.hashAlgorithm").value("SHA-256"))
                .andExpect(jsonPath("$.canonicalizationVersion").value("audit-record-canonical-v1"));
    }

    @Test
    void rejectsIntegrityVerificationMissingFromWithProblemDetail() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/audit-integrity/verifications", tenantId)
                        .header("Authorization", "Bearer read-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "to": "2026-06-08T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("from is required"));
    }

    @Test
    void rejectsIntegrityVerificationMissingToWithProblemDetail() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/audit-integrity/verifications", tenantId)
                        .header("Authorization", "Bearer read-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "from": "2026-06-07T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("to is required"));
    }

    @Test
    void rejectsAuditSearchValidationErrorsWithProblemDetail() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(auditSearchService.search(any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported audit search filter: borrowerName"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records", tenantId)
                        .header("Authorization", "Bearer read-token")
                        .param("borrowerName", "Alice Applicant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Unsupported audit search filter: borrowerName"));
    }

    @Test
    void returnsProblemDetailForValidationErrors() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuditRecord record = record(tenantId);

        mockMvc.perform(post("/internal/v1/tenants/{tenantId}/audit-records", tenantId)
                        .header("Authorization", "Bearer service-token")
                        .header("X-Correlation-Id", record.getCorrelationId().toString())
                        .header("X-Request-Id", record.getRequestId().toString())
                        .contentType("application/json")
                        .content(requestBody(record)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-Id", record.getCorrelationId().toString()))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key is required"))
                .andExpect(jsonPath("$.path").value("/internal/v1/tenants/" + tenantId + "/audit-records"))
                .andExpect(jsonPath("$.correlationId").value(record.getCorrelationId().toString()))
                .andExpect(jsonPath("$.requestId").value(record.getRequestId().toString()));
    }

    @Test
    void rejectsBodyCorrelationIdThatDoesNotMatchHeader() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuditRecord record = record(tenantId);

        mockMvc.perform(post("/internal/v1/tenants/{tenantId}/audit-records", tenantId)
                        .header("Authorization", "Bearer service-token")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(requestBody(record)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("correlationId must match X-Correlation-Id"));
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
