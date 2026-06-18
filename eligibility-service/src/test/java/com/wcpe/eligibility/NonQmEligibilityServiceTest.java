package com.wcpe.eligibility;

import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.*;
import com.wcpe.eligibility.nonqm.NonQmEligibilityService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.wcpe.eligibility.DscrFactCalculatorTest.rule;
import static org.junit.jupiter.api.Assertions.*;

class NonQmEligibilityServiceTest {
    private final NonQmEligibilityService service = new NonQmEligibilityService();

    @Test
    void productWithoutActiveRuleSetIsRejectedForCatalogIntegration() {
        NonQmEligibilityResult result = service.evaluate(new NonQmEligibilityRequest("NO_RULES", "INV-A", "BROKER", null, null,
            new ProductDefinition("NO_RULES", "NO_RATIO", "INV-A", "BROKER", Map.of(), null), null));

        assertFalse(result.eligible());
        assertEquals("NON_QM_RULE_SET_MISSING", result.outcomes().get(0).reasonCode());
        assertTrue(result.missingFacts().contains("nonQm.ruleSet"));
    }

    @Test
    void foreignNationalAndItinRulesUseSpecificFacts() {
        NonQmEligibilityResult foreignNational = service.evaluate(request("FN", "FOREIGN_NATIONAL",
            new NonQmScenarioFacts(null, null, new ForeignNationalFacts(true, true, "1234", false, true), null, null, null),
            rule("FN-VISA", "nonQm.foreignNational.visaPresent", Operator.EQ, true, "FOREIGN_NATIONAL_VISA_PRESENT")));

        NonQmEligibilityResult itin = service.evaluate(request("ITIN", "ITIN",
            new NonQmScenarioFacts(null, null, null, new ItinFacts("6789", false), null, null),
            rule("ITIN-PRESENT", "nonQm.itin.present", Operator.EQ, true, "ITIN_PRESENT")));

        assertTrue(foreignNational.eligible());
        assertTrue(itin.eligible());
        assertEquals(true, foreignNational.calculatedFacts().get("nonQm.foreignNational.visaPresent"));
        assertEquals(true, itin.calculatedFacts().get("nonQm.itin.present"));
    }

    @Test
    void noRatioAnd1099OnlyProductTypesAreRuleDriven() {
        NonQmEligibilityResult noRatio = service.evaluate(request("NO_RATIO", "NO_RATIO",
            new NonQmScenarioFacts(null, null, null, null, null, new NoRatioFacts(true)),
            rule("NO-RATIO-FLAG", "nonQm.noRatio.incomeExcludedFromDti", Operator.EQ, true, "NO_RATIO_INCOME_EXCLUDED")));

        NonQmEligibilityResult ten99 = service.evaluate(request("1099", "1099_ONLY",
            new NonQmScenarioFacts(null, null, null, null, new TenNinetyNineFacts(30, com.wcpe.eligibility.DscrFactCalculatorTest.bd("9000.00"), true), null),
            rule("1099-INCOME", "nonQm.1099.qualifyingMonthlyIncome", Operator.GTE, "8000.00", "1099_INCOME_PASS")));

        assertTrue(noRatio.eligible());
        assertTrue(ten99.eligible());
    }

    @Test
    void importedLoanPassRuleSetSelectionUsesQuoteDateAndReturnsSafeConfigRefsAndFieldMessages() {
        service.importRuleSet(new PpeRuleSetImportRequest(RuleSetSource.LOANPASS, "LP-MATRIX-OLD", "DSCR_30YR", "DSCR", "INV-A", "BROKER", 1,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-01T00:00:00Z"), List.of(Map.of(
                "ruleId", "LP-OLD", "priority", 1, "factPath", "nonQm.dscr.ratio", "operator", "GTE", "value", "1.0",
                "decision", "ELIGIBLE", "severity", "HARD_STOP", "reasonCode", "OLD_VERSION", "displayMessage", "Prior version",
                "ppeFieldRefs", Map.of("loanpassField", "DSCR_RATIO")))));
        service.importRuleSet(new PpeRuleSetImportRequest(RuleSetSource.LOANPASS, "LP-MATRIX-CURRENT", "DSCR_30YR", "DSCR", "INV-A", "BROKER", 2,
            Instant.parse("2026-06-01T00:00:00Z"), null, List.of(Map.of(
                "ruleId", "LP-CURRENT", "priority", 1, "factPath", "nonQm.dscr.ratio", "operator", "GTE", "value", "1.0",
                "decision", "ELIGIBLE", "severity", "HARD_STOP", "reasonCode", "CURRENT_VERSION", "displayMessage", "DSCR ratio was evaluated.",
                "ppeFieldRefs", Map.of("loanpassField", "DSCR_RATIO")))));

        NonQmEligibilityResult result = service.evaluate(new NonQmEligibilityRequest("DSCR_30YR", "INV-A", "BROKER", LocalDate.of(2026, 6, 15),
            new ScenarioFacts(new RentSchedule(com.wcpe.eligibility.DscrFactCalculatorTest.bd("2500.00")), new HousingExpense(com.wcpe.eligibility.DscrFactCalculatorTest.bd("2000.00")), null, null, null),
            new ProductDefinition("DSCR_30YR", "DSCR", "INV-A", "BROKER", Map.of(), null), null));

        assertTrue(result.eligible());
        assertEquals(2, result.ruleSetVersion());
        assertEquals("LP-MATRIX-CURRENT", result.ruleConfigRefs().get(0).sourceSystemRef());
        assertEquals("DSCR_RATIO", result.fieldMessages().get(0).fieldPath());
        assertFalse(result.ruleConfigRefs().toString().contains("configuredValue"));
    }

    private NonQmEligibilityRequest request(String code, String type, NonQmScenarioFacts facts, EligibilityRule rule) {
        NonQmEligibilityRuleSet ruleSet = new NonQmEligibilityRuleSet("rs-" + code, code, type, "INV-A", "BROKER", 1, null, null,
            List.of(rule), RuleSetSource.CATALOG, "catalog:" + code);
        return new NonQmEligibilityRequest(code, "INV-A", "BROKER", null, new ScenarioFacts(null, null, facts, null, null),
            new ProductDefinition(code, type, "INV-A", "BROKER", Map.of(), ruleSet), null);
    }
}
