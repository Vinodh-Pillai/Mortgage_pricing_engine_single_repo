package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.CompensationHookEvaluator.CapFloor;
import com.wcpe.adjustment.CompensationHookEvaluator.CompensationHookEvaluation;
import com.wcpe.adjustment.CompensationHookEvaluator.CompensationHookRequest;
import com.wcpe.adjustment.CompensationHookEvaluator.CompensationHookResult;
import com.wcpe.adjustment.CompensationHookEvaluator.CompensationHookRule;
import com.wcpe.adjustment.CompensationHookEvaluator.CompensationPlanSnapshot;
import com.wcpe.adjustment.CompensationHookEvaluator.EffectiveWindow;
import com.wcpe.adjustment.CompensationHookEvaluator.HookStatus;
import com.wcpe.adjustment.CompensationHookEvaluator.HookType;
import com.wcpe.adjustment.CompensationHookEvaluator.MappingStatus;
import com.wcpe.adjustment.CompensationHookEvaluator.TokenizedIdentifier;
import com.wcpe.adjustment.CompensationHookEvaluator.VisibilityPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompensationHookEvaluatorTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000068");
    private static final Instant QUOTE_DATE = Instant.parse("2026-05-01T00:00:00Z");

    private final CompensationHookEvaluator evaluator = new CompensationHookEvaluator();

    @Test
    void appliesApprovedPlanHooksWithPrecisionVisibilityEventAndAuditEvidence() {
        List<CompensationHookRule> rules = List.of(
            rule("map-bps", HookType.BPS_DELTA, Map.of("bps", new BigDecimal("12.3456")), new CapFloor(null, new BigDecimal("0.100000")), 1),
            rule("map-money", HookType.PERCENT_OF_LOAN_AMOUNT, Map.of("percent", new BigDecimal("0.250000")), null, 2),
            rule("map-credit", HookType.FEE_CREDIT, Map.of("moneyAmount", new BigDecimal("-75.555")), null, 3)
        );

        CompensationHookEvaluation result = evaluator.evaluate(request(rules));
        CompensationHookEvaluation replay = evaluator.evaluate(request(rules));

        assertThat(result.hookResults()).extracting(CompensationHookResult::mappingId)
            .containsExactly("map-bps", "map-money", "map-credit");
        assertThat(result.hookResults().get(0).pointsDelta()).isEqualByComparingTo("0.100000");
        assertThat(result.hookResults().get(0).visibilityLabel()).isEqualTo("Compensation");
        assertThat(result.hookResults().get(1).moneyAmount()).isEqualByComparingTo("625.00");
        assertThat(result.hookResults().get(2).moneyAmount()).isEqualByComparingTo("-75.56");
        assertThat(result.inputSnapshotHash()).hasSize(64).isEqualTo(replay.inputSnapshotHash());
        assertThat(result.event().eventType()).isEqualTo("QuoteAdjustmentApplied.v1");
        assertThat(result.event().key()).isEqualTo(TENANT_ID + ":quote-PII-06-S08");
        assertThat(result.event().mappingIds()).containsExactly("map-bps", "map-money", "map-credit");
        assertThat(result.audit().action()).isEqualTo("COMPENSATION_HOOK_EVALUATED");
        assertThat(result.audit().planSnapshotHash()).isEqualTo("plan-snapshot-hash-PII-06-S08");
    }

    @Test
    void failsClosedForMissingApprovedSnapshotStalePlanAndUnapprovedMapping() {
        CompensationHookRule approvedRule = rule("map-points", HookType.PRICE_POINTS_DELTA,
            Map.of("pointsDelta", new BigDecimal("0.050000")), null, 1);

        assertThatThrownBy(() -> evaluator.evaluate(request(snapshot(false), List.of(approvedRule))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires an approved compensation plan snapshot");

        CompensationPlanSnapshot staleSnapshot = new CompensationPlanSnapshot(
            TENANT_ID,
            "COMP-PLAN-A",
            4,
            true,
            "plan-snapshot-hash-PII-06-S08",
            new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"))
        );
        assertThatThrownBy(() -> evaluator.evaluate(request(staleSnapshot, List.of(approvedRule))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not effective for quote date");

        CompensationHookRule draftRule = new CompensationHookRule(
            "map-draft",
            TENANT_ID,
            "COMP-PLAN-A",
            4,
            HookType.LABEL_ONLY,
            Map.of("channel", "retail"),
            Map.of(),
            null,
            VisibilityPolicy.summary("Compensation"),
            "COMP_LABEL",
            1,
            MappingStatus.DRAFT,
            window(),
            "tok-requester",
            "tok-approver",
            "mapping-hash-draft"
        );
        assertThatThrownBy(() -> evaluator.evaluate(request(List.of(draftRule))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("mapping must be approved");
    }

    @Test
    void rejectsRawPersonnelIdentifiersAndSelfApprovalForMappings() {
        assertThatThrownBy(() -> new TokenizedIdentifier("loan officer@example.test"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be tokenized");

        assertThatThrownBy(() -> ruleWithMappingActors("raw requester@example.test", "tok-approver"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requestedByToken must be tokenized");

        assertThatThrownBy(() -> ruleWithMappingActors("tok-requester", "Raw Approver"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("approvedByToken must be tokenized");

        assertThatThrownBy(() -> ruleWithMappingActors("tok-same", "tok-same"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("separation of duties");
    }

    @Test
    void selectorMismatchSuppressesHookWithoutInventingFallbackPolicy() {
        CompensationHookRule wholesaleOnly = new CompensationHookRule(
            "map-wholesale",
            TENANT_ID,
            "COMP-PLAN-A",
            4,
            HookType.FIXED_MONEY_FEE,
            Map.of("channel", "wholesale"),
            Map.of("moneyAmount", new BigDecimal("123.45")),
            null,
            VisibilityPolicy.summary("Compensation"),
            "COMP_FEE",
            1,
            MappingStatus.APPROVED,
            window(),
            "tok-requester",
            "tok-approver",
            "mapping-hash-wholesale"
        );

        CompensationHookEvaluation result = evaluator.evaluate(request(List.of(wholesaleOnly)));

        assertThat(result.hookResults()).isEmpty();
        assertThat(result.event().mappingIds()).isEmpty();
        assertThat(result.inputSnapshotHash()).hasSize(64);
    }

    @Test
    void blockingPolicyAppendsBlockedWaterfallLineBeforeConflictDetection() {
        CompensationHookRule blockingRule = rule("map-block", HookType.BLOCKING_POLICY, Map.of(), null, 7);

        CompensationHookEvaluation result = evaluator.evaluate(request(List.of(blockingRule)));

        assertThat(result.hookResults()).hasSize(1);
        assertThat(result.hookResults().get(0).status()).isEqualTo(HookStatus.BLOCKED);
        assertThat(result.hookResults().get(0).waterfallSequence()).isEqualTo(10);
        assertThat(result.hookResults().get(0).formulaInputs()).containsEntry("blockingPolicy", "COMP_REASON");
    }

    private CompensationHookRequest request(List<CompensationHookRule> rules) {
        return request(snapshot(true), rules);
    }

    private CompensationHookRequest request(CompensationPlanSnapshot snapshot, List<CompensationHookRule> rules) {
        return new CompensationHookRequest(
            TENANT_ID,
            "quote-PII-06-S08",
            "scenario-PII-06-S08",
            new TokenizedIdentifier("tok-loan-officer-01"),
            new TokenizedIdentifier("tok-branch-01"),
            "retail",
            "configured-product",
            "configured-investor",
            new BigDecimal("250000.00"),
            new BigDecimal("101.125000"),
            snapshot,
            rules,
            10,
            QUOTE_DATE,
            Instant.parse("2026-05-01T00:00:01Z"),
            "correlation-PII-06-S08",
            "idem-PII-06-S08"
        );
    }

    private CompensationPlanSnapshot snapshot(boolean approved) {
        return new CompensationPlanSnapshot(
            TENANT_ID,
            "COMP-PLAN-A",
            4,
            approved,
            "plan-snapshot-hash-PII-06-S08",
            window()
        );
    }

    private CompensationHookRule rule(
        String mappingId,
        HookType type,
        Map<String, BigDecimal> formulaParameters,
        CapFloor capFloor,
        int priority
    ) {
        return new CompensationHookRule(
            mappingId,
            TENANT_ID,
            "COMP-PLAN-A",
            4,
            type,
            Map.of("channel", "retail", "product", "configured-product", "investor", "configured-investor"),
            formulaParameters,
            capFloor,
            VisibilityPolicy.summary("Compensation"),
            "COMP_REASON",
            priority,
            MappingStatus.APPROVED,
            window(),
            "tok-requester",
            "tok-approver",
            "mapping-hash-" + mappingId
        );
    }

    private CompensationHookRule ruleWithMappingActors(String requestedByToken, String approvedByToken) {
        return new CompensationHookRule(
            "map-actor-validation",
            TENANT_ID,
            "COMP-PLAN-A",
            4,
            HookType.LABEL_ONLY,
            Map.of("channel", "retail"),
            Map.of(),
            null,
            VisibilityPolicy.summary("Compensation"),
            "COMP_LABEL",
            1,
            MappingStatus.APPROVED,
            window(),
            requestedByToken,
            approvedByToken,
            "mapping-hash-actor-validation"
        );
    }

    private EffectiveWindow window() {
        return new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null);
    }
}
