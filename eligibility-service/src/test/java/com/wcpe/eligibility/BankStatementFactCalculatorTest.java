package com.wcpe.eligibility;

import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.*;
import com.wcpe.eligibility.nonqm.NonQmEligibilityService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.wcpe.eligibility.DscrFactCalculatorTest.bd;
import static com.wcpe.eligibility.DscrFactCalculatorTest.rule;
import static org.junit.jupiter.api.Assertions.*;

class BankStatementFactCalculatorTest {
    private final NonQmEligibilityService service = new NonQmEligibilityService();

    @Test
    void calculatesBusinessBankStatementIncomeAcrossTwelveMonths() {
        NonQmEligibilityResult result = service.evaluate(request(months(12), 12));

        assertTrue(result.eligible());
        assertEquals(new BigDecimal("1500.00"), result.calculatedFacts().get("nonQm.bankStatement.qualifyingMonthlyIncome"));
        assertEquals("BUSINESS", result.calculatedFacts().get("nonQm.bankStatement.statementType"));
    }

    @Test
    void missingRequiredMonthsFailsClosed() {
        NonQmEligibilityResult result = service.evaluate(request(months(2), 12));

        assertFalse(result.eligible());
        assertTrue(result.missingFacts().contains("nonQm.bankStatement.months"));
        assertEquals(EligibilityDecision.INELIGIBLE, result.outcomes().get(0).decision());
    }

    private NonQmEligibilityRequest request(List<BankStatementMonth> statements, int requestedMonths) {
        NonQmEligibilityRuleSet ruleSet = new NonQmEligibilityRuleSet("rs-bank", "BANK_12", "BANK_STATEMENT", "INV-A", "BROKER", 1, null, null,
            List.of(rule("BANK-STMT-MONTHS", "nonQm.bankStatement.months", Operator.EXISTS, null, "BANK_STATEMENT_MONTHS_PRESENT"),
                rule("BANK-STMT-INCOME", "nonQm.bankStatement.qualifyingMonthlyIncome", Operator.GTE, "1000.00", "BANK_STATEMENT_INCOME_PASS")), RuleSetSource.CATALOG, "catalog:BANK_12");
        BankStatementInput bankStatement = new BankStatementInput("BUSINESS", requestedMonths, statements, bd("0.25"));
        ScenarioFacts scenario = new ScenarioFacts(null, null, new NonQmScenarioFacts(bankStatement, null, null, null, null, null), null, null);
        return new NonQmEligibilityRequest("BANK_12", "INV-A", "BROKER", null, scenario,
            new ProductDefinition("BANK_12", "BANK_STATEMENT", "INV-A", "BROKER", Map.of("bankStatement.expenseFactor", "0.25"), ruleSet), null);
    }

    private List<BankStatementMonth> months(int count) {
        List<BankStatementMonth> months = new ArrayList<>();
        for (int i = 1; i <= count; i++) months.add(new BankStatementMonth("2026-%02d".formatted(i), bd("2000.00"), false));
        return months;
    }
}
