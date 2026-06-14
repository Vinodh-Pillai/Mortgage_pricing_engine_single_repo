package com.wcpe.eligibility;

import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.*;
import com.wcpe.eligibility.nonqm.NonQmEligibilityService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DscrFactCalculatorTest {
    private final NonQmEligibilityService service = new NonQmEligibilityService();

    @Test
    void derivesDscrRatioAndAppliesCatalogRuleThreshold() {
        NonQmEligibilityResult result = service.evaluate(new NonQmEligibilityRequest(
            "DSCR_30YR", "INV-A", "BROKER",
            null,
            new ScenarioFacts(new RentSchedule(bd("2500.00")), new HousingExpense(bd("2000.00")), null, null, null),
            product("DSCR_30YR", "DSCR", dscrRuleSet()),
            null
        ));

        assertTrue(result.eligible());
        assertEquals(EligibilityDecision.ELIGIBLE, result.decision());
        assertEquals(new BigDecimal("1.2500"), result.calculatedFacts().get("nonQm.dscr.ratio"));
        assertEquals("DSCR-MIN-001", result.outcomes().get(0).ruleId());
        assertTrue(result.auditHash().startsWith("sha256:"));
    }

    @Test
    void missingRentOrPitiaFailsClosedForHardStopRule() {
        NonQmEligibilityResult result = service.evaluate(new NonQmEligibilityRequest(
            "DSCR_30YR", "INV-A", "BROKER",
            null,
            new ScenarioFacts(new RentSchedule(null), new HousingExpense(bd("2000.00")), null, null, null),
            product("DSCR_30YR", "DSCR", dscrRuleSet()),
            null
        ));

        assertFalse(result.eligible());
        assertTrue(result.missingFacts().contains("nonQm.dscr.ratio"));
        assertEquals(EligibilityDecision.INELIGIBLE, result.outcomes().get(0).decision());
    }

    static NonQmEligibilityRuleSet dscrRuleSet() {
        return new NonQmEligibilityRuleSet("rs-dscr", "DSCR_30YR", "DSCR", "INV-A", "BROKER", 3, null, null,
            List.of(rule("DSCR-MIN-001", "nonQm.dscr.ratio", Operator.GTE, "1.0", "DSCR-MIN-PASS")), RuleSetSource.CATALOG, "catalog:DSCR_30YR");
    }

    static EligibilityRule rule(String id, String factPath, Operator operator, Object value, String reasonCode) {
        return new EligibilityRule(id, 10, EligibilitySeverity.HARD_STOP, List.of(new EligibilityCondition(factPath, operator, value, "catalog")), EligibilityDecision.ELIGIBLE, reasonCode, reasonCode, Map.of("field", factPath));
    }

    static ProductDefinition product(String code, String type, NonQmEligibilityRuleSet ruleSet) {
        return new ProductDefinition(code, type, "INV-A", "BROKER", Map.of(), ruleSet);
    }

    static BigDecimal bd(String value) { return new BigDecimal(value); }
}
