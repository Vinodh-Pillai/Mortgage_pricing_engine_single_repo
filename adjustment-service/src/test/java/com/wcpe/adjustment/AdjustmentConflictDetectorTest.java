package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.AdjustmentConflictDetector.AdjustedTotals;
import com.wcpe.adjustment.AdjustmentConflictDetector.ConflictDetectionConfiguration;
import com.wcpe.adjustment.AdjustmentConflictDetector.ConflictDetectionRequest;
import com.wcpe.adjustment.AdjustmentConflictDetector.ConflictDetectionResult;
import com.wcpe.adjustment.AdjustmentConflictDetector.ConflictLineRef;
import com.wcpe.adjustment.AdjustmentConflictDetector.ConflictPolicy;
import com.wcpe.adjustment.AdjustmentConflictDetector.ConflictPolicySet;
import com.wcpe.adjustment.AdjustmentConflictDetector.ConflictSeverity;
import com.wcpe.adjustment.AdjustmentConflictDetector.ConflictStatus;
import com.wcpe.adjustment.AdjustmentConflictDetector.EffectiveWindow;
import com.wcpe.adjustment.AdjustmentConflictDetector.MatchCriterion;
import com.wcpe.adjustment.AdjustmentConflictDetector.PolicySetStatus;
import com.wcpe.adjustment.AdjustmentConflictDetector.ResolutionStrategy;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AdjustmentConflictDetectorTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000069");
    private static final UUID POLICY_SET_ID = UUID.fromString("20000000-0000-0000-0000-000000000069");
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000069");
    private static final Instant QUOTE_DATE = Instant.parse("2026-04-01T00:00:00Z");

    private final AdjustmentConflictDetector detector = new AdjustmentConflictDetector();

    @Test
    void blocksDuplicateAdjustmentLinesForConfiguredPolicyWithoutHardCodedBusinessValues() {
        ConflictPolicy policy = policy("DUPLICATE_REASON", ConflictSeverity.BLOCKING,
            ResolutionStrategy.FAIL_ON_MULTIPLE, Set.of("LLPA"), List.of(MatchCriterion.CATEGORY, MatchCriterion.REASON_CODE), Map.of());
        ConflictDetectionRequest request = request(policy, ConflictDetectionConfiguration.empty(), List.of(
            line("llpa-fico-1", "LLPA", "FICO_BAND", "fico-group", "0.125000", "0.00", 1),
            line("llpa-fico-2", "LLPA", "FICO_BAND", "fico-group", "0.250000", "0.00", 2)
        ));

        ConflictDetectionResult result = detector.detect(request);
        ConflictDetectionResult replay = detector.detect(request);

        assertThat(result.status()).isEqualTo(ConflictStatus.BLOCKED);
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).affectedLineIds()).containsExactly("llpa-fico-1", "llpa-fico-2");
        assertThat(result.conflicts().get(0).reasonCode()).isEqualTo("CONFIGURED_REASON");
        assertThat(result.event().eventType()).isEqualTo("QuoteAdjustmentConflictsDetected.v1");
        assertThat(result.audit().action()).isEqualTo("ADJUSTMENT_CONFLICT_DETECTION_COMPLETED");
        assertThat(result.inputSnapshotHash()).hasSize(64).isEqualTo(replay.inputSnapshotHash());
        assertThat(result.audit().resultHash()).hasSize(64).isEqualTo(replay.audit().resultHash());
    }

    @Test
    void suppressesLowerPriorityDuplicateLinesAndKeepsSourceLinesImmutable() {
        ConflictPolicy policy = policy("AUTO_SUPPRESS_DUPLICATE", ConflictSeverity.WARNING,
            ResolutionStrategy.SUPPRESS_LOWER_PRIORITY, Set.of("LLPA"), List.of(MatchCriterion.CATEGORY, MatchCriterion.EXCLUSIVITY_GROUP), Map.of());
        ConflictLineRef selected = line("selected", "LLPA", "OCCUPANCY", "occupancy-group", "0.125000", "0.00", 1);
        ConflictLineRef suppressed = line("suppressed", "LLPA", "OCCUPANCY", "occupancy-group", "0.375000", "0.00", 5);

        ConflictDetectionResult result = detector.detect(request(policy, ConflictDetectionConfiguration.empty(), List.of(suppressed, selected)));

        assertThat(result.status()).isEqualTo(ConflictStatus.AUTO_RESOLVED);
        assertThat(result.suppressedLineIds()).containsExactly("suppressed");
        assertThat(result.conflicts().get(0).resolutionAction()).isEqualTo("SUPPRESS_LOWER_PRIORITY");
        assertThat(selected.pointsDelta()).isEqualByComparingTo("0.125000");
        assertThat(suppressed.pointsDelta()).isEqualByComparingTo("0.375000");
    }

    @Test
    void capsTotalPointsUsingConfiguredReferencesAndRecordsAdjustedTotals() {
        ConflictPolicy policy = policy("CAP_TOTAL_LLPA_POINTS", ConflictSeverity.WARNING,
            ResolutionStrategy.CAP_TOTAL_POINTS, Set.of("LLPA"), List.of(), Map.of("capPointsConfigRef", "llpa-total-cap"));
        ConflictDetectionConfiguration configuration = new ConflictDetectionConfiguration(Map.of(
            "llpa-total-cap", new BigDecimal("0.500000")
        ));

        ConflictDetectionResult result = detector.detect(request(policy, configuration, List.of(
            line("cashout", "LLPA", "CASH_OUT", "cashout", "0.375000", "0.00", 1),
            line("fico", "LLPA", "FICO_BAND", "fico", "0.250000", "0.00", 2)
        )));

        assertThat(result.status()).isEqualTo(ConflictStatus.AUTO_RESOLVED);
        assertThat(result.adjustedTotals()).containsExactly(new AdjustedTotals("LLPA", new BigDecimal("0.625000"), new BigDecimal("0.500000"), "CAP_TOTAL_POINTS"));
        assertThat(result.conflicts().get(0).computedValue()).isEqualByComparingTo("0.625000");
        assertThat(result.conflicts().get(0).configuredLimit()).isEqualByComparingTo("0.500000");
    }

    @Test
    void missingConfigurationAndUnpublishedPolicySetsFailClosed() {
        ConflictPolicy capPolicy = policy("CAP_TOTAL_LLPA_POINTS", ConflictSeverity.WARNING,
            ResolutionStrategy.CAP_TOTAL_POINTS, Set.of("LLPA"), List.of(), Map.of("capPointsConfigRef", "missing-cap"));

        assertThatThrownBy(() -> detector.detect(request(capPolicy, ConflictDetectionConfiguration.empty(), List.of(
            line("cashout", "LLPA", "CASH_OUT", "cashout", "0.375000", "0.00", 1)
        ))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing configured numeric value");

        ConflictPolicySet draft = policySet(PolicySetStatus.DRAFT, List.of(capPolicy));
        assertThatThrownBy(() -> detector.detect(request(draft, ConflictDetectionConfiguration.empty(), List.of(
            line("cashout", "LLPA", "CASH_OUT", "cashout", "0.375000", "0.00", 1)
        ))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("published conflict policy set");
    }

    @Test
    void loadsGoldenBlockedResponseFixtureAndComparesDetectorOutput() throws IOException {
        String fixture = Files.readString(Path.of("src/test/resources/golden/PII-06-S09-conflicts/conflict-detect-blocked-response.json"));
        ConflictPolicy policy = policy("DUPLICATE_REASON", ConflictSeverity.BLOCKING,
            ResolutionStrategy.FAIL_ON_MULTIPLE, Set.of("LLPA"), List.of(MatchCriterion.CATEGORY, MatchCriterion.REASON_CODE), Map.of());

        ConflictDetectionResult result = detector.detect(request(policy, ConflictDetectionConfiguration.empty(), List.of(
            line("llpa-fico-1", "LLPA", "FICO_BAND", "fico-group", "0.125000", "0.00", 1),
            line("llpa-fico-2", "LLPA", "FICO_BAND", "fico-group", "0.250000", "0.00", 2)
        )));

        assertThat(result.conflictRunId().toString()).isEqualTo(jsonString(fixture, "conflictRunId"));
        assertThat(result.status().name()).isEqualTo(jsonString(fixture, "status"));
        assertThat(result.policyVersion()).isEqualTo(jsonInteger(fixture, "policyVersion"));
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).policyCode()).isEqualTo(jsonString(fixture, "policyCode"));
        assertThat(result.conflicts().get(0).severity().name()).isEqualTo(jsonString(fixture, "severity"));
        assertThat(result.conflicts().get(0).affectedLineIds()).containsExactlyElementsOf(jsonStringArray(fixture, "affectedLineIds"));
        assertThat(result.conflicts().get(0).resolutionAction()).isEqualTo(jsonString(fixture, "resolutionAction"));
        assertThat(result.conflicts().get(0).reasonCode()).isEqualTo(jsonString(fixture, "reasonCode"));
        assertThat(result.suppressedLineIds()).containsExactlyElementsOf(jsonStringArray(fixture, "suppressedLineIds"));
        assertThat(result.event().eventType()).isEqualTo(jsonString(fixture, "eventType"));
        assertThat(result.audit().action()).isEqualTo(jsonString(fixture, "auditAction"));
    }

    @Test
    void capsTotalMoneyUsingConfiguredReferencesAndRecordsAdjustedTotals() {
        ConflictPolicy policy = policy("CAP_TOTAL_FEE_MONEY", ConflictSeverity.WARNING,
            ResolutionStrategy.CAP_TOTAL_MONEY, Set.of("FEE"), List.of(), Map.of("capMoneyConfigRef", "fee-total-cap"));
        ConflictDetectionConfiguration configuration = new ConflictDetectionConfiguration(Map.of(
            "fee-total-cap", new BigDecimal("250.00")
        ));

        ConflictDetectionResult result = detector.detect(request(policy, configuration, List.of(
            line("state-fee", "FEE", "STATE_SURCHARGE", "state-fees", "0.000000", "175.10", 1),
            line("county-fee", "FEE", "COUNTY_SURCHARGE", "state-fees", "0.000000", "100.15", 2)
        )));

        assertThat(result.status()).isEqualTo(ConflictStatus.AUTO_RESOLVED);
        assertThat(result.adjustedTotals()).containsExactly(new AdjustedTotals("FEE", new BigDecimal("275.25"), new BigDecimal("250.00"), "CAP_TOTAL_MONEY"));
        assertThat(result.conflicts().get(0).computedValue()).isEqualByComparingTo("275.25");
        assertThat(result.conflicts().get(0).configuredLimit()).isEqualByComparingTo("250.00");
        assertThat(result.conflicts().get(0).resolutionAction()).isEqualTo("CAP_TOTAL_MONEY");
    }

    @Test
    void highestCostStrategySuppressesAllButConfiguredHighestCostLine() {
        ConflictPolicy policy = policy("HIGHEST_COST_DUPLICATE", ConflictSeverity.WARNING,
            ResolutionStrategy.HIGHEST_COST, Set.of("LLPA"), List.of(MatchCriterion.CATEGORY, MatchCriterion.EXCLUSIVITY_GROUP), Map.of());

        ConflictDetectionResult result = detector.detect(request(policy, ConflictDetectionConfiguration.empty(), List.of(
            line("low-cost", "LLPA", "OCCUPANCY", "occupancy-group", "0.125000", "10.00", 1),
            line("highest-cost", "LLPA", "OCCUPANCY", "occupancy-group", "0.625000", "250.00", 3),
            line("middle-cost", "LLPA", "OCCUPANCY", "occupancy-group", "0.250000", "100.00", 2)
        )));

        assertThat(result.status()).isEqualTo(ConflictStatus.AUTO_RESOLVED);
        assertThat(result.suppressedLineIds()).containsExactly("low-cost", "middle-cost");
        assertThat(result.conflicts().get(0).resolutionAction()).isEqualTo("HIGHEST_COST");
    }

    @Test
    void lowestCostStrategySuppressesAllButConfiguredLowestCostLine() {
        ConflictPolicy policy = policy("LOWEST_COST_DUPLICATE", ConflictSeverity.WARNING,
            ResolutionStrategy.LOWEST_COST, Set.of("FEE"), List.of(MatchCriterion.CATEGORY, MatchCriterion.EXCLUSIVITY_GROUP), Map.of());

        ConflictDetectionResult result = detector.detect(request(policy, ConflictDetectionConfiguration.empty(), List.of(
            line("lowest-cost", "FEE", "STATE_FEE", "state-fee-group", "0.000000", "10.00", 3),
            line("middle-cost", "FEE", "STATE_FEE", "state-fee-group", "0.000000", "95.00", 2),
            line("highest-cost", "FEE", "STATE_FEE", "state-fee-group", "0.000000", "150.00", 1)
        )));

        assertThat(result.status()).isEqualTo(ConflictStatus.AUTO_RESOLVED);
        assertThat(result.suppressedLineIds()).containsExactly("highest-cost", "middle-cost");
        assertThat(result.conflicts().get(0).resolutionAction()).isEqualTo("LOWEST_COST");
    }

    @Test
    void warnOnlyStrategyRecordsWarningWithoutSuppressionOrBlocking() {
        ConflictPolicy policy = policy("WARN_ONLY_DUPLICATE", ConflictSeverity.WARNING,
            ResolutionStrategy.WARN_ONLY, Set.of("LLPA"), List.of(MatchCriterion.CATEGORY, MatchCriterion.REASON_CODE), Map.of());

        ConflictDetectionResult result = detector.detect(request(policy, ConflictDetectionConfiguration.empty(), List.of(
            line("warning-1", "LLPA", "PRODUCT_OVERLAY", "overlay", "0.125000", "0.00", 1),
            line("warning-2", "LLPA", "PRODUCT_OVERLAY", "overlay", "0.250000", "0.00", 2)
        )));

        assertThat(result.status()).isEqualTo(ConflictStatus.WARN);
        assertThat(result.suppressedLineIds()).isEmpty();
        assertThat(result.adjustedTotals()).isEmpty();
        assertThat(result.conflicts().get(0).resolutionAction()).isEqualTo("WARN_ONLY");
        assertThat(result.conflicts().get(0).severity()).isEqualTo(ConflictSeverity.WARNING);
    }

    @Test
    void requireManualReviewStrategyBlocksWhenConfiguredAsBlockingPolicy() {
        ConflictPolicy policy = policy("REQUIRE_MANUAL_REVIEW_DUPLICATE", ConflictSeverity.BLOCKING,
            ResolutionStrategy.REQUIRE_MANUAL_REVIEW, Set.of("LLPA"), List.of(MatchCriterion.CATEGORY, MatchCriterion.EXCLUSIVITY_GROUP), Map.of());

        ConflictDetectionResult result = detector.detect(request(policy, ConflictDetectionConfiguration.empty(), List.of(
            line("review-1", "LLPA", "COMPENSATION_OVERLAY", "manual-review-group", "0.125000", "0.00", 1),
            line("review-2", "LLPA", "COMPENSATION_OVERLAY", "manual-review-group", "0.250000", "0.00", 2)
        )));

        assertThat(result.status()).isEqualTo(ConflictStatus.BLOCKED);
        assertThat(result.suppressedLineIds()).isEmpty();
        assertThat(result.conflicts().get(0).resolutionAction()).isEqualTo("REQUIRE_MANUAL_REVIEW");
        assertThat(result.conflicts().get(0).severity()).isEqualTo(ConflictSeverity.BLOCKING);
    }

    private ConflictDetectionRequest request(ConflictPolicy policy, ConflictDetectionConfiguration configuration, List<ConflictLineRef> lines) {
        return request(policySet(PolicySetStatus.PUBLISHED, List.of(policy)), configuration, lines);
    }

    private ConflictDetectionRequest request(ConflictPolicySet policySet, ConflictDetectionConfiguration configuration, List<ConflictLineRef> lines) {
        return new ConflictDetectionRequest(
            TENANT_ID,
            RUN_ID,
            "quote-PII-06-S09",
            "scenario-PII-06-S09",
            QUOTE_DATE,
            lines,
            List.of(),
            List.of(),
            policySet,
            configuration,
            "actor-1",
            "correlation-PII-06-S09",
            "idem-PII-06-S09",
            Instant.parse("2026-04-01T00:00:01Z")
        );
    }

    private static ConflictPolicySet policySet(PolicySetStatus status, List<ConflictPolicy> policies) {
        return new ConflictPolicySet(
            TENANT_ID,
            POLICY_SET_ID,
            1,
            status,
            new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
            policies,
            "requester-1",
            status == PolicySetStatus.PUBLISHED ? "approver-1" : null,
            status == PolicySetStatus.PUBLISHED ? Instant.parse("2026-01-02T00:00:00Z") : null,
            null
        );
    }

    private static ConflictPolicy policy(
        String policyCode,
        ConflictSeverity severity,
        ResolutionStrategy strategy,
        Set<String> categories,
        List<MatchCriterion> criteria,
        Map<String, String> formulaParameters
    ) {
        return new ConflictPolicy(
            UUID.nameUUIDFromBytes(policyCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            policyCode,
            severity,
            categories,
            criteria,
            strategy,
            formulaParameters,
            "Configured remediation for " + policyCode,
            "CONFIGURED_REASON",
            1,
            true,
            "configured-policy-source"
        );
    }

    private static ConflictLineRef line(String lineId, String category, String reasonCode, String exclusivityGroup, String points, String money, int priority) {
        return new ConflictLineRef(
            lineId,
            category,
            reasonCode,
            exclusivityGroup,
            null,
            null,
            null,
            new BigDecimal(points),
            new BigDecimal(money),
            priority,
            "configured-ledger-source"
        );
    }

    private static String jsonString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        assertThat(matcher.find()).as("fixture contains string key %s", key).isTrue();
        return matcher.group(1);
    }

    private static int jsonInteger(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        assertThat(matcher.find()).as("fixture contains integer key %s", key).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private static List<String> jsonStringArray(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        assertThat(matcher.find()).as("fixture contains array key %s", key).isTrue();
        String arrayBody = matcher.group(1).trim();
        if (arrayBody.isEmpty()) {
            return List.of();
        }
        Matcher valueMatcher = Pattern.compile("\\\"([^\\\"]*)\\\"").matcher(arrayBody);
        List<String> values = new java.util.ArrayList<>();
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1));
        }
        return values;
    }
}
