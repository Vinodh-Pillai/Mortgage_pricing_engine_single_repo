package com.wcpe.eligibility;

import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.*;
import com.wcpe.eligibility.nonqm.NonQmEligibilityService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.wcpe.eligibility.DscrFactCalculatorTest.bd;
import static com.wcpe.eligibility.DscrFactCalculatorTest.rule;
import static org.junit.jupiter.api.Assertions.*;

class AssetDepletionFactCalculatorTest {
    private final NonQmEligibilityService service = new NonQmEligibilityService();

    @Test
    void calculatesAssetDepletionMonthlyIncomeFromAssetsAndMonthsRemaining() {
        NonQmEligibilityRuleSet ruleSet = new NonQmEligibilityRuleSet("rs-asset", "ASSET_60", "ASSET_DEPLETION", "INV-A", "BROKER", 1, null, null,
            List.of(rule("ASSET-DEPLETION-INCOME", "nonQm.assetDepletion.qualifyingMonthlyIncome", Operator.GTE, "5000.00", "ASSET_DEPLETION_INCOME_PASS")), RuleSetSource.CATALOG, "catalog:ASSET_60");
        AssetDepletionInput assetInput = new AssetDepletionInput(bd("600000.00"), 120);
        ScenarioFacts scenario = new ScenarioFacts(null, null, new NonQmScenarioFacts(null, assetInput, null, null, null, null), null, null);

        NonQmEligibilityResult result = service.evaluate(new NonQmEligibilityRequest("ASSET_60", "INV-A", "BROKER", null, scenario,
            new ProductDefinition("ASSET_60", "ASSET_DEPLETION", "INV-A", "BROKER", Map.of(), ruleSet), null));

        assertTrue(result.eligible());
        assertEquals(new BigDecimal("5000.00"), result.calculatedFacts().get("nonQm.assetDepletion.qualifyingMonthlyIncome"));
    }
}
