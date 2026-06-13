package com.wcpe.eligibility;

import com.wcpe.eligibility.domain.models.InvestorEligibilityBatchEvaluationRequest;
import com.wcpe.eligibility.domain.models.InvestorEligibilityMatrixRow;
import com.wcpe.eligibility.domain.models.InvestorEligibilityEvaluationRequest;
import com.wcpe.eligibility.domain.models.InvestorEligibilityResult;
import com.wcpe.eligibility.domain.models.LoanScenario;
import com.wcpe.eligibility.service.InvestorEligibilityEvaluationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvestorEligibilityEvaluationServiceTest {
    private final InvestorEligibilityEvaluationService service = new InvestorEligibilityEvaluationService();
    private static final UUID INVESTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test void minFicoFailure() { assertCode(scenario(619, "96.00", "96.00", "40.00", "CA", "06037", "400000"), row(Map.of()), "MIN_FICO"); }
    @Test void maxLtvFailure() { assertCode(scenario(700, "98.00", "98.00", "40.00", "CA", "06037", "400000"), row(Map.of()), "MAX_LTV"); }
    @Test void maxDtiFailure() { assertCode(scenario(700, "96.00", "96.00", "46.00", "CA", "06037", "400000"), row(Map.of()), "MAX_DTI"); }
    @Test void stateExclusion() { assertCode(scenario(700, "96.00", "96.00", "40.00", "TX", "48029", "400000"), row(Map.of()), "STATE_NOT_ALLOWED"); }
    @Test void propertyTypeExclusion() { assertCode(new LoanScenario(700, bd("90.00"), bd("90.00"), bd("40.00"), "PURCHASE", "CONDO", "PRIMARY", "CA", "06037", bd("400000")), row(Map.of()), "PROPERTY_TYPE_INELIGIBLE"); }

    @Test
    void allDimensionsAndBatchEvaluatePassWhenInsideMatrix() {
        InvestorEligibilityResult result = evaluate(scenario(720, "90.00", "95.00", "40.00", "CA", "06037", "400000"), row(Map.of()));
        assertTrue(result.eligible());
        assertTrue(service.batchEvaluate(new InvestorEligibilityBatchEvaluationRequest(resultScenario(), LocalDate.parse("2026-06-12"), List.of(request(resultScenario(), row(Map.of()))))).results().get(0).eligible());
    }

    @Test
    void countyCltvLoanAmountPurposeAndOccupancyFailuresAreReturned() {
        LoanScenario scenario = new LoanScenario(720, bd("90.00"), bd("98.00"), bd("40.00"), "PURCHASE", "SFR", "INVESTMENT", "CA", "06059", bd("900000"));
        InvestorEligibilityMatrixRow investorOccupancyRow = new InvestorEligibilityMatrixRow(UUID.randomUUID(), "PURCHASE", "SFR", "INVESTMENT", 620, null, bd("97.00"), bd("97.00"), bd("45.00"), bd("100000"), bd("750000"), List.of("CA"), List.of(), Map.of("allowedLoanPurposes", List.of("RATE_TERM_REFI"), "allowedOccupancyTypes", List.of("PRIMARY"), "excludedCounties", List.of("06059")), LocalDate.parse("2026-01-01"), null, true);
        InvestorEligibilityResult result = evaluate(scenario, investorOccupancyRow);
        assertFalse(result.eligible());
        assertTrue(result.failures().stream().map(f -> f.code()).toList().containsAll(List.of("MAX_CLTV", "MAX_LOAN_AMOUNT", "LOAN_PURPOSE_INELIGIBLE", "OCCUPANCY_INELIGIBLE", "COUNTY_EXCLUDED")));
    }

    @Test
    void overlayMostRestrictive() {
        InvestorEligibilityResult result = evaluate(scenario(700, "91.00", "91.00", "44.00", "CA", "06037", "400000"), row(Map.of("minFico", 720, "maxLtv", "90.00", "highLtvMaxDti", Map.of("triggerLtv", "90.00", "maxDti", "43.00"))));
        assertFalse(result.eligible());
        assertTrue(result.failures().stream().map(f -> f.code()).toList().containsAll(List.of("MIN_FICO", "MAX_LTV", "MAX_DTI")));
        assertEquals(720, result.thresholds().get("min_fico"));
    }

    @Test
    void effectiveDating() {
        InvestorEligibilityMatrixRow expired = new InvestorEligibilityMatrixRow(UUID.randomUUID(), "PURCHASE", "SFR", "PRIMARY", 800, null, bd("50.00"), bd("50.00"), bd("20.00"), null, null, List.of("CA"), List.of(), Map.of(), LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), true);
        InvestorEligibilityResult result = evaluate(scenario(700, "96.00", "96.00", "40.00", "CA", "06037", "400000"), expired);
        assertTrue(result.eligible());
        assertEquals("NO_ACTIVE_MATRIX_ROW", result.warnings().get(0));
    }

    private void assertCode(LoanScenario scenario, InvestorEligibilityMatrixRow row, String code) {
        assertTrue(evaluate(scenario, row).failures().stream().anyMatch(f -> code.equals(f.code())));
    }

    private InvestorEligibilityResult evaluate(LoanScenario scenario, InvestorEligibilityMatrixRow row) { return service.evaluate(request(scenario, row)); }
    private InvestorEligibilityEvaluationRequest request(LoanScenario scenario, InvestorEligibilityMatrixRow row) { return new InvestorEligibilityEvaluationRequest(INVESTOR, "Fixture Investor", scenario, LocalDate.parse("2026-06-12"), List.of(row)); }
    private LoanScenario resultScenario() { return scenario(720, "90.00", "95.00", "40.00", "CA", "06037", "400000"); }
    private LoanScenario scenario(int fico, String ltv, String cltv, String dti, String state, String county, String amount) { return new LoanScenario(fico, bd(ltv), bd(cltv), bd(dti), "PURCHASE", "SFR", "PRIMARY", state, county, bd(amount)); }
    private InvestorEligibilityMatrixRow row(Map<String, Object> overlays) { return new InvestorEligibilityMatrixRow(UUID.randomUUID(), "PURCHASE", "SFR", "PRIMARY", 620, null, bd("97.00"), bd("97.00"), bd("45.00"), bd("100000"), bd("750000"), List.of("CA"), List.of(), overlays, LocalDate.parse("2026-01-01"), null, true); }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
