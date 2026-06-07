package com.wcpe.auditreplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.EvidenceExportService;
import com.wcpe.auditreplay.domain.EvidenceExportJob;
import com.wcpe.auditreplay.filter.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvidenceExportApiContractTest {

    private final EvidenceExportService evidenceExportService = mock(EvidenceExportService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EvidenceExportController(evidenceExportService, objectMapper))
            .addFilters(new CorrelationIdFilter())
            .setControllerAdvice(new AuditProblemDetailsAdvice())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    @Test
    void matchesJobAndDownloadSchema() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        EvidenceExportJob job = job(tenantId, exportId, sourceId);
        when(evidenceExportService.requestExport(any())).thenReturn(job);
        when(evidenceExportService.getExport(tenantId, exportId)).thenReturn(job);
        when(evidenceExportService.recordDownload(tenantId, exportId, "audit-user-1", "download-idem")).thenReturn(job);
        when(evidenceExportService.manifest(job)).thenReturn(objectMapper.readTree(job.getManifestJson()));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/evidence-exports", tenantId)
                        .header("Authorization", "Bearer audit-token")
                        .header("Idempotency-Key", "idem-evidence-export")
                        .header("X-Correlation-Id", job.getCorrelationId().toString())
                        .contentType("application/json")
                        .content(requestBody(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportId").value(exportId.toString()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.manifest.schemaVersion").value("evidence-export-manifest-v1"))
                .andExpect(jsonPath("$.resultSummary.manifestHash").value("manifest-hash"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/evidence-exports/{exportId}/download", tenantId, exportId)
                        .header("Authorization", "Bearer audit-token")
                        .header("Idempotency-Key", "download-idem")
                        .header("X-Actor-Id", "audit-user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactUri").value("audit-replay://evidence-exports/" + exportId + "/manifest"))
                .andExpect(jsonPath("$.manifestHash").value("manifest-hash"));
    }

    @Test
    void rejectsMissingIdempotencyKeyWithProblemDetail() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/evidence-exports", tenantId)
                        .header("Authorization", "Bearer audit-token")
                        .contentType("application/json")
                        .content(requestBody(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key is required"));
    }

    private String requestBody(UUID sourceId) {
        return """
                {
                  "sourceAuditRecordIds": ["%s"],
                  "replayRunIds": ["%s"],
                  "purpose": "regulator support package",
                  "format": "ZIP_JSON",
                  "redactionProfile": "REGULATOR_SUMMARY",
                  "includeSnapshots": true,
                  "includeDiffs": true,
                  "includeHashes": true,
                  "requestedBy": "audit-user-1"
                }
                """.formatted(sourceId, UUID.randomUUID());
    }

    private EvidenceExportJob job(UUID tenantId, UUID exportId, UUID sourceId) {
        String manifest = """
                {"schemaVersion":"evidence-export-manifest-v1","sourceAuditRecordIds":["%s"],"files":[]}
                """.formatted(sourceId);
        return EvidenceExportJob.ready(
                tenantId,
                exportId,
                "audit-user-1",
                "regulator support package",
                "ZIP_JSON",
                "REGULATOR_SUMMARY",
                "{\"auditRecordIds\":[\"%s\"]}".formatted(sourceId).getBytes(StandardCharsets.UTF_8),
                "audit-replay://evidence-exports/" + exportId + "/manifest",
                manifest.getBytes(StandardCharsets.UTF_8),
                "manifest-hash",
                null,
                LocalDate.parse("2033-06-07"),
                false,
                UUID.randomUUID(),
                "idem-evidence-export",
                "request-hash");
    }
}
