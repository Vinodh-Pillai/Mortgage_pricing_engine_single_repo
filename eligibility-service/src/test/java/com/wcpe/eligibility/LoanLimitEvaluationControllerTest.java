package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.controller.EligibilityApiController;
import com.wcpe.eligibility.domain.models.LoanLimitDecision;
import com.wcpe.eligibility.domain.models.LoanLimitEvaluationResult;
import com.wcpe.eligibility.service.EligibilityApplicationService;
import com.wcpe.eligibility.service.EligibilityExplanationService;
import com.wcpe.eligibility.service.FicoLtvMatrixService;
import com.wcpe.eligibility.service.InvestorOverlayRuleService;
import com.wcpe.eligibility.service.LoanLimitEvaluationService;
import com.wcpe.eligibility.service.OccupancyPurposeRuleService;
import com.wcpe.eligibility.service.PropertyTypeRuleService;
import com.wcpe.eligibility.service.QuoteSubmissionApplicationService;
import com.wcpe.eligibility.cache.EligibilityCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EligibilityApiController.class)
class LoanLimitEvaluationControllerTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_VERSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean EligibilityApplicationService eligibilityApplicationService;
    @MockBean JdbcTemplate jdbcTemplate;
    @MockBean FicoLtvMatrixService ficoLtvMatrixService;
    @MockBean OccupancyPurposeRuleService occupancyPurposeRuleService;
    @MockBean PropertyTypeRuleService propertyTypeRuleService;
    @MockBean InvestorOverlayRuleService investorOverlayRuleService;
    @MockBean EligibilityCacheService eligibilityCacheService;
    @MockBean EligibilityExplanationService eligibilityExplanationService;
    @MockBean QuoteSubmissionApplicationService quoteSubmissionApplicationService;
    @MockBean LoanLimitEvaluationService loanLimitEvaluationService;

    @Test
    void returnsOkForCompletedLoanLimitEvaluation() throws Exception {
        when(loanLimitEvaluationService.evaluate(eq(TENANT_ID), any(), any()))
            .thenReturn(result("INELIGIBLE", "HARD_STOP", "LOAN_AMOUNT_EXCEEDS_CONFORMING_LIMIT"));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/loan-limit", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.decisions[0].ruleCode").value("CONF_LOAN_LIMIT"))
            .andExpect(jsonPath("$.decisions[0].reasonCode").value("LOAN_AMOUNT_EXCEEDS_CONFORMING_LIMIT"));
    }

    @Test
    void returns422ForMissingCountyDecision() throws Exception {
        when(loanLimitEvaluationService.evaluate(eq(TENANT_ID), any(), any()))
            .thenReturn(result("INSUFFICIENT_DATA", "WARNING", "MISSING_COUNTY"));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/loan-limit", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.decisions[0].reasonCode").value("MISSING_COUNTY"));
    }

    private LoanLimitEvaluationResult result(String status, String severity, String reasonCode) {
        return new LoanLimitEvaluationResult(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "COMPLETED",
            List.of(new LoanLimitDecision(PRODUCT_VERSION_ID, "CONF30", "FNMA", "CONF_LOAN_LIMIT", status, severity, reasonCode,
                "Loan amount 850000.00 exceeds 806500.00 for TX/Travis/1 unit.", new BigDecimal("806500.00"),
                new BigDecimal("850000.00"), UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"))),
            "sha256:test"
        );
    }

    private Map<String, Object> request() {
        return Map.of(
            "scenarioId", "cf6e0657-e55d-4485-8e7a-968cc8758fc0",
            "scenarioVersion", 1,
            "asOfDate", "2026-05-13",
            "productCandidates", List.of(Map.of("productVersionId", PRODUCT_VERSION_ID.toString(), "productCode", "CONF30", "investorCode", "FNMA")),
            "facts", Map.of("loanAmount", "850000.00", "propertyState", "TX", "propertyCounty", "Travis", "units", 1, "loanPurpose", "PURCHASE")
        );
    }
}
