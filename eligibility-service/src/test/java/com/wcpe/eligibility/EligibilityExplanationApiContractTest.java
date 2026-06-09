package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.controller.EligibilityApiController;
import com.wcpe.eligibility.domain.models.EligibilityExplanationResponse;
import com.wcpe.eligibility.service.EligibilityExplanationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EligibilityExplanationApiContractTest {
    @Test
    void successV1() throws Exception {
        EligibilityExplanationService explanationService = mock(EligibilityExplanationService.class);
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID quoteId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID quoteOptionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(explanationService.getExplanation(eq(tenantId), eq(quoteId), eq(quoteOptionId), eq("actor-1"), any(Set.class), eq("corr-1")))
            .thenReturn(response(quoteId, quoteOptionId));

        mockMvc(explanationService).perform(get("/api/v1/tenants/11111111-1111-1111-1111-111111111111/quotes/22222222-2222-2222-2222-222222222222/options/33333333-3333-3333-3333-333333333333/eligibility-explanation")
                .header("X-Actor-Id", "actor-1")
                .header("X-Permissions", "quote:read")
                .header("X-Correlation-Id", "corr-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quoteId").value("22222222-2222-2222-2222-222222222222"))
            .andExpect(jsonPath("$.summary.passed").value(1))
            .andExpect(jsonPath("$.rules[0].ruleCode").value("CONF_LOAN_LIMIT"))
            .andExpect(jsonPath("$.rules[0].inputFactRefs[0]").value("fact:loanAmount"))
            .andExpect(jsonPath("$.rules[0].overlayRefs[0]").value("overlay:loan-limit"))
            .andExpect(jsonPath("$.rules[0].cacheFreshnessStatus").value("FRESH"))
            .andExpect(jsonPath("$.audit.resultHash").value("sha256:result"));
    }

    @Test
    void notFoundNoLeakageV1() throws Exception {
        EligibilityExplanationService explanationService = mock(EligibilityExplanationService.class);
        when(explanationService.getExplanation(any(), any(), any(), any(), any(Set.class), any()))
            .thenThrow(new EligibilityExplanationService.ExplanationNotFoundException());

        mockMvc(explanationService).perform(get("/api/v1/tenants/11111111-1111-1111-1111-111111111111/quotes/22222222-2222-2222-2222-222222222222/options/33333333-3333-3333-3333-333333333333/eligibility-explanation")
                .header("X-Permissions", "quote:read"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("QUOTE_OPTION_NOT_FOUND"));
    }

    private MockMvc mockMvc(EligibilityExplanationService explanationService) {
        return MockMvcBuilders.standaloneSetup(new EligibilityApiController(
            mock(),
            mock(JdbcTemplate.class),
            new ObjectMapper().findAndRegisterModules(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            explanationService
        )).build();
    }

    private EligibilityExplanationResponse response(UUID quoteId, UUID quoteOptionId) {
        return new EligibilityExplanationResponse(
            quoteId,
            quoteOptionId,
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            1,
            "CONF30",
            "FNMA",
            "ELIGIBLE",
            new EligibilityExplanationResponse.Summary(1, 0, 0, 0),
            List.of(new EligibilityExplanationResponse.Rule("CONF_LOAN_LIMIT", "Loan limit", "ELIGIBLE", "INFO", "OK", "message", "$100,000.00", "$806,500.00", List.of("fact:loanAmount"), List.of("overlay:loan-limit"), "FRESH", "cache:eligibility:loan-limit", "rv1", "ev1", null)),
            new EligibilityExplanationResponse.Audit(UUID.fromString("55555555-5555-5555-5555-555555555555"), "sha256:result", "sha256:graph")
        );
    }
}
