package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.models.RuleDecision;
import com.wcpe.eligibility.repository.EligibilityPersistRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EligibilityPersistRepositoryReasonCodeTest {
    @Test
    void blankReasonCodeFallbackUsesStatusAndSeverityForNonPassDecisions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EligibilityPersistRepository repository = new EligibilityPersistRepository(jdbc, new ObjectMapper());
        EligibilityResult result = new EligibilityResult(
            UUID.randomUUID(),
            "tenant-a",
            "scenario-a",
            "WARNING",
            List.of(
                decision("R_PASS", "ELIGIBLE", "PASS", null),
                decision("R_FAIL", "INELIGIBLE", "HARD_STOP", " "),
                decision("R_WARN", "WARNING", "WARNING", ""),
                decision("R_UNKNOWN", null, "PASS", null)
            ),
            "result-hash",
            "request-hash",
            "catalog-v1",
            "rules-v1",
            "fixture-v1",
            Instant.parse("2026-07-01T00:00:00Z")
        );

        repository.saveEvaluation(UUID.randomUUID(), result);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(6)).update(sqlCaptor.capture(), argsCaptor.capture());

        List<String> persistedReasonCodes = IntStream.range(0, sqlCaptor.getAllValues().size())
            .filter(i -> sqlCaptor.getAllValues().get(i).contains("eligibility.eligibility_decision"))
            .mapToObj(i -> (String) argsCaptor.getAllValues().get(i)[8])
            .toList();

        assertEquals(List.of(
            "R_PASS_PASS",
            "R_FAIL_INELIGIBLE_HARD_STOP",
            "R_WARN_WARNING",
            "R_UNKNOWN_STATUS_UNSPECIFIED_PASS_SEVERITY"
        ), persistedReasonCodes);
        assertFalse(persistedReasonCodes.stream()
            .filter(code -> !code.startsWith("R_PASS_"))
            .anyMatch(code -> code.endsWith("_PASS")),
            "non-pass decisions must not fall back to a plain _PASS reason code");
    }

    private RuleDecision decision(String ruleCode, String status, String severity, String reasonCode) {
        return new RuleDecision(
            UUID.randomUUID(),
            "CONF30",
            "FNMA",
            ruleCode,
            ruleCode + " name",
            severity,
            status,
            reasonCode,
            ruleCode + " message",
            null,
            null,
            Map.of("rule", ruleCode)
        );
    }
}
