package com.wcpe.eligibility;

import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.*;
import com.wcpe.eligibility.nonqm.NonQmEligibilityService;
import com.wcpe.eligibility.repository.NonQmRuleSetStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PpeEligibilityMigrationTest {
    private final NonQmEligibilityService service = new NonQmEligibilityService(new FakeRuleSetStore());

    @Test
    void importsAndExportsLoanPassRuleSetWithoutLosingFieldRefsOrDecisionSemantics() {
        NonQmEligibilityRuleSet imported = service.importRuleSet(new PpeRuleSetImportRequest(RuleSetSource.LOANPASS, "LP-MATRIX-42", "DSCR_30YR", "DSCR", "INV-A", "BROKER", 7,
            List.of(Map.of(
                "ruleId", "LP-DSCR-MIN",
                "priority", 5,
                "factPath", "nonQm.dscr.ratio",
                "operator", "GTE",
                "value", "1.0",
                "decision", "ELIGIBLE",
                "severity", "HARD_STOP",
                "reasonCode", "DSCR_MIN_PASS",
                "ppeFieldRefs", Map.of("loanpassField", "DSCR_RATIO")
            ))));

        PpeRuleSetExportResponse exported = service.exportRuleSet(imported.ruleSetId(), "LOANPASS");

        assertEquals("LOANPASS", exported.format());
        assertEquals("DSCR_30YR", exported.payload().get("productCode"));
        assertTrue(exported.payload().toString().contains("DSCR_RATIO"));
        assertTrue(exported.payload().toString().contains("ELIGIBLE"));
    }

    private static final class FakeRuleSetStore implements NonQmRuleSetStore {
        private final List<NonQmEligibilityRuleSet> ruleSets = new ArrayList<>();

        @Override
        public void save(NonQmEligibilityRuleSet ruleSet) {
            ruleSets.removeIf(existing -> existing.ruleSetId().equals(ruleSet.ruleSetId()));
            ruleSets.add(ruleSet);
        }

        @Override
        public Optional<NonQmEligibilityRuleSet> findById(String ruleSetId) {
            return ruleSets.stream().filter(ruleSet -> ruleSet.ruleSetId().equals(ruleSetId)).findFirst();
        }

        @Override
        public Optional<NonQmEligibilityRuleSet> resolve(String productCode, String investorCode, String channelCode, Instant asOf) {
            return ruleSets.stream().findFirst();
        }
    }
}
