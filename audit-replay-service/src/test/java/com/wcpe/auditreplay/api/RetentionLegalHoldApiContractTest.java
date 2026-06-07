package com.wcpe.auditreplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.RetentionLegalHoldService;
import com.wcpe.auditreplay.domain.LegalHold;
import com.wcpe.auditreplay.domain.RetentionPolicy;
import com.wcpe.auditreplay.domain.RetentionPurgeRun;
import com.wcpe.auditreplay.domain.RetentionPurgeRunStatus;
import com.wcpe.auditreplay.filter.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RetentionLegalHoldApiContractTest {

    private final RetentionLegalHoldService service = mock(RetentionLegalHoldService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RetentionLegalHoldController(service, objectMapper))
            .addFilters(new CorrelationIdFilter())
            .setControllerAdvice(new AuditProblemDetailsAdvice())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    @Test
    void matchesPolicyHoldRunSchemas() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RetentionPolicy policy = RetentionPolicy.publish(
                policyId, tenantId, "AUDIT_RECORD", "US-FED", "CONVENTIONAL", "RETAIL", 2555,
                LocalDate.parse("2026-01-01"), null, "policy-author", "policy-approver", correlationId, "policy-idem");
        LegalHold hold = LegalHold.apply(
                holdId, tenantId, "CASE-2026-001", "{\"auditRecordIds\":[]}".getBytes(StandardCharsets.UTF_8),
                "regulator inquiry", "hold-user", "hold-approver", correlationId, "hold-idem");
        RetentionPurgeRun run = RetentionPurgeRun.create(
                runId, tenantId, LocalDate.parse("2026-01-01"), 3, 1, 2, RetentionPurgeRunStatus.COMPLETED,
                "{\"schemaVersion\":\"retention-purge-report-v1\"}".getBytes(StandardCharsets.UTF_8),
                "report-hash", "retention-admin", correlationId, "purge-idem");
        when(service.publishPolicy(any())).thenReturn(policy);
        when(service.applyHold(any())).thenReturn(hold);
        when(service.runPurge(any())).thenReturn(run);
        when(service.report(run)).thenReturn(objectMapper.readTree(run.getReportJson()));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/retention-policies", tenantId)
                        .header("Authorization", "Bearer audit-token")
                        .header("Idempotency-Key", "policy-idem")
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType("application/json")
                        .content(policyBody(policyId, correlationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(policyId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.resultSummary.retentionDays").value(2555));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/legal-holds", tenantId)
                        .header("Authorization", "Bearer audit-token")
                        .header("Idempotency-Key", "hold-idem")
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType("application/json")
                        .content(holdBody(holdId, correlationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(holdId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.resultSummary.caseId").value("CASE-2026-001"));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/retention-runs", tenantId)
                        .header("Authorization", "Bearer audit-token")
                        .header("Idempotency-Key", "purge-idem")
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType("application/json")
                        .content(purgeBody(correlationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.resultSummary.skippedHeldCount").value(2))
                .andExpect(jsonPath("$.report.schemaVersion").value("retention-purge-report-v1"));
    }

    @Test
    void mapsMissingPolicyConfigToPolicyNotSatisfied() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        when(service.publishPolicy(any())).thenThrow(new RetentionLegalHoldService.PolicyNotSatisfiedException("tenant retentionDays policy is required"));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/retention-policies", tenantId)
                        .header("Authorization", "Bearer audit-token")
                        .header("Idempotency-Key", "policy-idem")
                        .header("X-Correlation-Id", correlationId.toString())
                        .contentType("application/json")
                        .content(policyBody(UUID.randomUUID(), correlationId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_SATISFIED"));
    }

    private String policyBody(UUID policyId, UUID correlationId) {
        return """
                {
                  "policyId": "%s",
                  "evidenceType": "AUDIT_RECORD",
                  "jurisdiction": "US-FED",
                  "productFilter": "CONVENTIONAL",
                  "channelFilter": "RETAIL",
                  "retentionDays": 2555,
                  "effectiveFrom": "2026-01-01",
                  "createdBy": "policy-author",
                  "approvedBy": "policy-approver",
                  "correlationId": "%s"
                }
                """.formatted(policyId, correlationId);
    }

    private String holdBody(UUID holdId, UUID correlationId) {
        return """
                {
                  "holdId": "%s",
                  "caseId": "CASE-2026-001",
                  "scope": {"auditRecordIds": []},
                  "reason": "regulator inquiry",
                  "appliedBy": "hold-user",
                  "approvedBy": "hold-approver",
                  "correlationId": "%s"
                }
                """.formatted(holdId, correlationId);
    }

    private String purgeBody(UUID correlationId) {
        return """
                {
                  "cutoff": "2026-01-01",
                  "dryRun": false,
                  "requestedBy": "retention-admin",
                  "correlationId": "%s"
                }
                """.formatted(correlationId);
    }
}
