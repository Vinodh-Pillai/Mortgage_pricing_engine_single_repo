package com.wcpe.auditreplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.LockReplayResult;
import com.wcpe.auditreplay.application.LockReplayService;
import com.wcpe.auditreplay.domain.LockReplayMode;
import com.wcpe.auditreplay.domain.LockReplayRun;
import com.wcpe.auditreplay.domain.LockReplayStatus;
import com.wcpe.auditreplay.filter.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LockReplayApiContractTest {

    private final LockReplayService lockReplayService = mock(LockReplayService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LockReplayController(lockReplayService))
            .addFilters(new CorrelationIdFilter())
            .setControllerAdvice(new AuditProblemDetailsAdvice())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    @Test
    void matchesRunDiffSchema() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID auditRecordId = UUID.randomUUID();
        LockReplayRun run = run(tenantId, runId, auditRecordId, LockReplayStatus.MISMATCH, "EXPIRATION_MISMATCH");
        LockReplayResult result = new LockReplayResult(
                run,
                objectMapper.readTree("{\"classification\":\"MISMATCH\",\"lockStateMutated\":false}"),
                "audit-replay://lock-replays/" + runId + "/diff");
        when(lockReplayService.startReplay(any())).thenReturn(result);
        when(lockReplayService.getReplay(tenantId, runId)).thenReturn(result);
        when(lockReplayService.getDiff(tenantId, runId)).thenReturn(result.diff());

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/lock-replays", tenantId)
                        .header("Authorization", "Bearer replay-token")
                        .header("Idempotency-Key", "idem-lock-replay")
                        .header("X-Correlation-Id", run.getCorrelationId().toString())
                        .contentType("application/json")
                        .content(requestBody(auditRecordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("MISMATCH"))
                .andExpect(jsonPath("$.decisionType").value("EXPIRATION"))
                .andExpect(jsonPath("$.mismatchCode").value("EXPIRATION_MISMATCH"))
                .andExpect(jsonPath("$.marketSnapshotRef").value("market-snapshot-2026-06-07"))
                .andExpect(jsonPath("$.diff.lockStateMutated").value(false))
                .andExpect(jsonPath("$.evidenceExportRef").value("audit-replay://lock-replays/" + runId + "/diff"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/lock-replays/{runId}/diff", tenantId, runId)
                        .header("Authorization", "Bearer replay-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("MISMATCH"));
    }

    @Test
    void rejectsMissingIdempotencyKeyWithProblemDetail() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/lock-replays", tenantId)
                        .header("Authorization", "Bearer replay-token")
                        .contentType("application/json")
                        .content(requestBody(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key is required"));
    }

    private String requestBody(UUID auditRecordId) {
        return """
                {
                  "sourceAuditRecordId": "%s",
                  "sourceLockId": "lock-123",
                  "decisionType": "EXPIRATION",
                  "mode": "DIAGNOSE",
                  "reason": "audit investigation",
                  "requestedBy": "audit-user-1",
                  "expectedHash": "expected-output-hash"
                }
                """.formatted(auditRecordId);
    }

    private LockReplayRun run(UUID tenantId, UUID runId, UUID auditRecordId, LockReplayStatus status, String mismatchCode) {
        return LockReplayRun.completed(
                tenantId,
                runId,
                "lock-123",
                auditRecordId,
                "audit-user-1",
                "audit investigation",
                LockReplayMode.DIAGNOSE,
                "EXPIRATION",
                status,
                "original-output-hash",
                "replay-output-hash",
                mismatchCode,
                "{\"lockPolicyVersionRefs\":[\"lock-policy-v1\"]}".getBytes(StandardCharsets.UTF_8),
                "market-snapshot-2026-06-07",
                "extension-policy-v1",
                "audit-record:" + auditRecordId,
                Instant.parse("2026-06-07T10:00:00Z"),
                Instant.parse("2026-06-07T10:00:01Z"),
                UUID.randomUUID(),
                "idem-lock-replay",
                "request-hash");
    }
}
