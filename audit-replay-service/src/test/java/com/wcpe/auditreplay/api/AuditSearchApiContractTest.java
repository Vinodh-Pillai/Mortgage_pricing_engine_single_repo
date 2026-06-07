package com.wcpe.auditreplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.AuditIntegrityHashService;
import com.wcpe.auditreplay.application.AuditRecorder;
import com.wcpe.auditreplay.application.AuditSearchService;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditSearchApiContractTest {

    private final AuditSearchService auditSearchService = mock(AuditSearchService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuditRecordController(
                    mock(AuditRecorder.class),
                    mock(AuditRecordRepository.class),
                    auditSearchService,
                    mock(AuditIntegrityHashService.class),
                    objectMapper))
            .setControllerAdvice(new AuditProblemDetailsAdvice())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    @Test
    void matchesProblemDetailsAndPageSchema() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        when(auditSearchService.search(any(), any())).thenReturn(new AuditSearchService.AuditSearchPage(
                List.of(new AuditSearchService.AuditSearchResult(
                        recordId,
                        Instant.parse("2026-06-07T10:00:00Z").toString(),
                        "QUOTE_CREATED",
                        "quote",
                        "quote-123",
                        "actor-hash",
                        "SUCCESS",
                        UUID.randomUUID(),
                        "VERIFIED",
                        true,
                        LocalDate.parse("2033-06-07"))),
                "next-cursor",
                1,
                true));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records", tenantId)
                        .header("Authorization", "Bearer read-token")
                        .param("from", "2026-06-07T00:00:00Z")
                        .param("to", "2026-06-08T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].auditRecordId").value(recordId.toString()))
                .andExpect(jsonPath("$.results[0].occurredAt").value("2026-06-07T10:00:00Z"))
                .andExpect(jsonPath("$.results[0].subjectType").value("quote"))
                .andExpect(jsonPath("$.results[0].actorIdHash").value("actor-hash"))
                .andExpect(jsonPath("$.results[0].integrityStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasMore").value(true));

        when(auditSearchService.search(any(), any()))
                .thenThrow(new IllegalArgumentException("Bounded date range is required: from and to"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit-records", tenantId)
                        .header("Authorization", "Bearer read-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Bounded date range is required: from and to"));
    }
}
