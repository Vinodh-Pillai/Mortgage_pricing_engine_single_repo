package com.wcpe.auditreplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.QuoteReplayResult;
import com.wcpe.auditreplay.application.QuoteReplayService;
import com.wcpe.auditreplay.domain.QuoteReplayMode;
import com.wcpe.auditreplay.domain.QuoteReplayRun;
import com.wcpe.auditreplay.domain.QuoteReplayStatus;
import com.wcpe.auditreplay.filter.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QuoteReplayApiContractTest {

    private final QuoteReplayService quoteReplayService = mock(QuoteReplayService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new QuoteReplayController(quoteReplayService))
            .addFilters(new CorrelationIdFilter())
            .setControllerAdvice(new AuditProblemDetailsAdvice())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    @Test
    void matchesRunAndDiffSchema() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID auditRecordId = UUID.randomUUID();
        QuoteReplayRun run = run(tenantId, runId, auditRecordId, QuoteReplayStatus.MISMATCH, "OUTPUT_HASH_MISMATCH");
        QuoteReplayResult result = new QuoteReplayResult(
                run,
                objectMapper.readTree("{\"classification\":\"MISMATCH\",\"quoteStateMutated\":false}"),
                "audit-replay://quote-replays/" + runId + "/diff");
        when(quoteReplayService.startReplay(any())).thenReturn(result);
        when(quoteReplayService.getReplay(tenantId, runId)).thenReturn(result);
        when(quoteReplayService.getDiff(tenantId, runId)).thenReturn(result.diff());

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/quote-replays", tenantId)
                        .header("Authorization", "Bearer replay-token")
                        .header("Idempotency-Key", "idem-quote-replay")
                        .header("X-Correlation-Id", run.getCorrelationId().toString())
                        .contentType("application/json")
                        .content(requestBody(auditRecordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("MISMATCH"))
                .andExpect(jsonPath("$.mismatchCode").value("OUTPUT_HASH_MISMATCH"))
                .andExpect(jsonPath("$.diff.quoteStateMutated").value(false))
                .andExpect(jsonPath("$.evidenceExportRef").value("audit-replay://quote-replays/" + runId + "/diff"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/quote-replays/{runId}/diff", tenantId, runId)
                        .header("Authorization", "Bearer replay-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("MISMATCH"));
    }

    @Test
    void rejectsMissingIdempotencyKeyWithProblemDetail() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/quote-replays", tenantId)
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
                  "mode": "DIAGNOSE",
                  "reason": "audit investigation",
                  "requestedBy": "audit-user-1",
                  "expectedHash": "expected-output-hash"
                }
                """.formatted(auditRecordId);
    }

    private QuoteReplayRun run(UUID tenantId, UUID runId, UUID auditRecordId, QuoteReplayStatus status, String mismatchCode) {
        return QuoteReplayRun.completed(
                tenantId,
                runId,
                "quote-123",
                auditRecordId,
                "audit-user-1",
                "audit investigation",
                QuoteReplayMode.DIAGNOSE,
                status,
                "original-output-hash",
                "replay-output-hash",
                mismatchCode,
                "{\"pricingConfig\":\"version-ref-1\"}".getBytes(StandardCharsets.UTF_8),
                "audit-record:" + auditRecordId,
                Instant.parse("2026-06-07T10:00:00Z"),
                Instant.parse("2026-06-07T10:00:01Z"),
                UUID.randomUUID(),
                "idem-quote-replay",
                "request-hash");
    }
}
