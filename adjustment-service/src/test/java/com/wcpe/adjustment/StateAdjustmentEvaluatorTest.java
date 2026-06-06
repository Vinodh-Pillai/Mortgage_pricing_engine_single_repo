package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.adjustment.StateAdjustmentEvaluator.EvaluationRequest;
import com.wcpe.adjustment.StateAdjustmentEvaluator.EvaluationStatus;
import com.wcpe.adjustment.StateAdjustmentEvaluator.StateAdjustmentResult;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class StateAdjustmentEvaluatorTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000065");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000165");
    private static final UUID RULE_BOOK_ID = UUID.fromString("20000000-0000-0000-0000-000000000065");
    private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000065");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void JurisdictionSpecificityTest_countyRuleOverridesStateRule() {
        JurisdictionAdjustmentRule stateRule = rule(RULE_ID, "CA", null, null, "POINTS_DELTA", "0.125000", null, null, 1);
        JurisdictionAdjustmentRule countyRule = rule(
            UUID.fromString("30000000-0000-0000-0000-000000000066"),
            "CA",
            "06037",
            null,
            "POINTS_DELTA",
            "0.250000",
            null,
            null,
            2
        );

        StateAdjustmentResult result = new StateAdjustmentEvaluator().evaluate(request(), List.of(stateRule, countyRule));

        assertThat(result.status()).isEqualTo(EvaluationStatus.APPLIED);
        assertThat(result.category()).isEqualTo("LLPA_STATE");
        assertThat(result.jurisdictionKey()).isEqualTo("CA:06037:*");
        assertThat(result.specificityRank()).isEqualTo(1);
        assertThat(result.pointsDelta()).isEqualByComparingTo("0.250000");
        assertThat(result.bpsDelta()).isEqualByComparingTo("25.0000");
        assertThat(result.appliedRuleIds()).containsExactly(countyRule.ruleId());
        assertThat(result.suppressedRuleIds()).isEmpty();
        assertThat(result.ruleContentHash()).hasSize(64);
    }

    @Test
    void StateAdjustmentFormulaTest_splitsPointAndMoneyImpactsIntoLedgerLines() {
        JurisdictionAdjustmentRule points = rule(RULE_ID, "CA", "06037", null, "POINTS_DELTA", "0.125000", null, null, 1);
        JurisdictionAdjustmentRule percent = rule(
            UUID.fromString("30000000-0000-0000-0000-000000000067"),
            "CA",
            "06037",
            null,
            "PERCENT_OF_LOAN_AMOUNT",
            "0.500000",
            null,
            null,
            2
        );

        StateAdjustmentResult result = new StateAdjustmentEvaluator().evaluate(request(), List.of(points, percent));

        assertThat(result.status()).isEqualTo(EvaluationStatus.APPLIED);
        assertThat(result.pointsDelta()).isEqualByComparingTo("0.125000");
        assertThat(result.bpsDelta()).isEqualByComparingTo("12.5000");
        assertThat(result.moneyImpact()).isEqualByComparingTo("2000.00");
        assertThat(result.ledgerLines()).extracting("category").containsExactly("LLPA_STATE", "FEE_STATE");
        assertThat(result.ledgerLines().get(0).reasonCode()).isEqualTo("CONFIGURED_STATE_REASON");
        assertThat(result.priceAfterStateAdjustment()).isEqualByComparingTo("100.125000");
    }

    @Test
    void StateAdjustmentCapFloorTest_appliesConfiguredCapAfterPercentFormula() {
        JurisdictionAdjustmentRule capped = rule(
            RULE_ID,
            "CA",
            "06037",
            null,
            "PERCENT_OF_LOAN_AMOUNT",
            "1.000000",
            "2500.00",
            "1000.00",
            1
        );

        StateAdjustmentResult result = new StateAdjustmentEvaluator().evaluate(request(), List.of(capped));

        assertThat(result.moneyImpact()).isEqualByComparingTo("2500.00");
        assertThat(result.ledgerLines().get(0).rawAmount()).isEqualByComparingTo("4000.00");
        assertThat(result.ledgerLines().get(0).capApplied()).isTrue();
        assertThat(result.ledgerLines().get(0).floorApplied()).isFalse();
    }

    @Test
    void StateAdjustmentAmbiguityTest_failsClosedForSameSpecificitySamePriorityPointRules() {
        JurisdictionAdjustmentRule first = rule(RULE_ID, "CA", "06037", null, "POINTS_DELTA", "0.125000", null, null, 1);
        JurisdictionAdjustmentRule second = rule(
            UUID.fromString("30000000-0000-0000-0000-000000000068"),
            "CA",
            "06037",
            null,
            "POINTS_DELTA",
            "0.250000",
            null,
            null,
            1
        );

        StateAdjustmentResult result = new StateAdjustmentEvaluator().evaluate(request(), List.of(first, second));

        assertThat(result.status()).isEqualTo(EvaluationStatus.CONFLICT);
        assertThat(result.validationMessages()).containsExactly("ADJ_STATE_RULE_AMBIGUOUS");
        assertThat(result.pointsDelta()).isZero();
    }

    @Test
    void StateAdjustmentRequiredFieldsTest_failsClosedWhenPropertyStateMissing() {
        StateAdjustmentResult result = new StateAdjustmentEvaluator().evaluate(request(null, "06037", null), List.of(rule()));

        assertThat(result.status()).isEqualTo(EvaluationStatus.FAIL_CLOSED);
        assertThat(result.validationMessages()).containsExactly("ADJ_STATE_MISSING");
        assertThat(result.appliedRuleIds()).isEmpty();
    }

    @Test
    void StateAdjustmentTenantIsolationIT_ignoresOtherTenantRulesAndFailsClosed() {
        JurisdictionAdjustmentRule otherTenantRule = new JurisdictionAdjustmentRule(
            OTHER_TENANT_ID,
            RULE_BOOK_ID,
            RULE_ID,
            "v1",
            StateCode.of("CA"),
            "06037",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            "POINTS_DELTA",
            new BigDecimal("0.125000"),
            null,
            null,
            "CONFIGURED_STATE_REASON",
            "CONFIGURED_SOURCE_REF",
            1,
            false,
            null,
            START,
            null,
            null,
            true
        );

        StateAdjustmentResult result = new StateAdjustmentEvaluator().evaluate(request(), List.of(otherTenantRule));

        assertThat(result.status()).isEqualTo(EvaluationStatus.FAIL_CLOSED);
        assertThat(result.validationMessages()).containsExactly("ADJ_JURISDICTION_UNSUPPORTED");
    }

    @Test
    void StateAdjustmentComplianceAuditIT_blocksComplianceRuleAndRetainsSourceReference() {
        JurisdictionAdjustmentRule block = new JurisdictionAdjustmentRule(
            TENANT_ID,
            RULE_BOOK_ID,
            RULE_ID,
            "v1",
            StateCode.of("CA"),
            "06037",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            "FIXED_MONEY",
            new BigDecimal("0.00"),
            null,
            null,
            "CONFIGURED_BLOCK_REASON",
            "CONFIGURED_SOURCE_REF",
            1,
            true,
            null,
            START,
            null,
            null,
            true
        );

        StateAdjustmentResult result = new StateAdjustmentEvaluator().evaluate(request(), List.of(block));

        assertThat(result.status()).isEqualTo(EvaluationStatus.COMPLIANCE_BLOCK);
        assertThat(result.category()).isEqualTo("COMPLIANCE_JURISDICTION_BLOCK");
        assertThat(result.validationMessages()).containsExactly("ADJ_STATE_COMPLIANCE_BLOCK");
        assertThat(result.ledgerLines().get(0).sourceRef()).isEqualTo("CONFIGURED_SOURCE_REF");
        assertThat(result.inputSnapshotHash()).hasSize(64);
    }

    @Test
    void StateAdjustmentSelectorSpecificityTest_requiresConfiguredSelectorsAndLoanAmountBandToMatch() {
        JurisdictionAdjustmentRule wrongProduct = ruleWithSelectors(
            UUID.fromString("30000000-0000-0000-0000-000000000069"),
            "other-product",
            "configured-investor",
            "configured-channel",
            "CONFIGURED_PURPOSE",
            "CONFIGURED_OCCUPANCY",
            "CONFIGURED_PROPERTY",
            "300000.00",
            "450000.00",
            "0.375000"
        );
        JurisdictionAdjustmentRule wrongAmountBand = ruleWithSelectors(
            UUID.fromString("30000000-0000-0000-0000-000000000070"),
            "configured-product",
            "configured-investor",
            "configured-channel",
            "CONFIGURED_PURPOSE",
            "CONFIGURED_OCCUPANCY",
            "CONFIGURED_PROPERTY",
            "450000.01",
            null,
            "0.500000"
        );
        JurisdictionAdjustmentRule matchingSelectors = ruleWithSelectors(
            UUID.fromString("30000000-0000-0000-0000-000000000071"),
            "configured-product",
            "configured-investor",
            "configured-channel",
            "CONFIGURED_PURPOSE",
            "CONFIGURED_OCCUPANCY",
            "CONFIGURED_PROPERTY",
            "300000.00",
            "450000.00",
            "0.625000"
        );

        StateAdjustmentEvaluator evaluator = new StateAdjustmentEvaluator();

        assertThat(evaluator.evaluate(request(), List.of(wrongProduct, wrongAmountBand)).status())
            .isEqualTo(EvaluationStatus.FAIL_CLOSED);

        StateAdjustmentResult result = evaluator.evaluate(request(), List.of(wrongProduct, wrongAmountBand, matchingSelectors));

        assertThat(result.status()).isEqualTo(EvaluationStatus.APPLIED);
        assertThat(result.appliedRuleIds()).containsExactly(matchingSelectors.ruleId());
        assertThat(result.pointsDelta()).isEqualByComparingTo("0.625000");
    }

    @Test
    void StateAdjustmentGoldenReplayTest_replaysCommittedRequestResponseAndErrorFixtures() throws Exception {
        String requestFixture = readGoldenFixture("state-adjustment-percent-request.json");
        String responseFixture = readGoldenFixture("state-adjustment-county-override-response.json");
        String errorFixture = readGoldenFixture("state-adjustment-ambiguous-error.json");
        EvaluationRequest fixtureRequest = requestFromFixture(requestFixture);
        JurisdictionAdjustmentRule stateRule = rule(RULE_ID, "CA", null, null, "POINTS_DELTA", "0.125000", null, null, 1);
        JurisdictionAdjustmentRule countyRule = rule(
            UUID.fromString("30000000-0000-0000-0000-000000000066"),
            "CA",
            "06037",
            null,
            "POINTS_DELTA",
            "0.250000",
            null,
            null,
            2
        );

        StateAdjustmentResult replay = new StateAdjustmentEvaluator().evaluate(fixtureRequest, List.of(stateRule, countyRule));

        assertThat(replay.status().name()).isEqualTo(jsonString(responseFixture, "status"));
        assertThat(replay.category()).isEqualTo(jsonString(responseFixture, "category"));
        assertThat(replay.jurisdictionKey()).isEqualTo(jsonString(responseFixture, "jurisdictionKey"));
        assertThat(replay.specificityRank()).isEqualTo(Integer.parseInt(jsonNumber(responseFixture, "specificityRank")));
        assertThat(replay.pointsDelta()).isEqualByComparingTo(jsonString(responseFixture, "pointsDelta"));
        assertThat(replay.bpsDelta()).isEqualByComparingTo(jsonString(responseFixture, "bpsDelta"));
        assertThat(replay.moneyImpact()).isEqualByComparingTo(jsonString(responseFixture, "moneyImpact"));
        assertThat(replay.ledgerLines().get(0).sourceRef()).isEqualTo(jsonString(responseFixture, "sourceRef"));

        JurisdictionAdjustmentRule duplicate = rule(
            UUID.fromString("30000000-0000-0000-0000-000000000068"),
            "CA",
            "06037",
            null,
            "POINTS_DELTA",
            "0.500000",
            null,
            null,
            1
        );
        StateAdjustmentResult conflict = new StateAdjustmentEvaluator().evaluate(fixtureRequest, List.of(rule(), duplicate));

        assertThat(conflict.status().name()).isEqualTo(jsonString(errorFixture, "status"));
        assertThat(conflict.validationMessages()).containsExactly(jsonArrayFirstString(errorFixture, "validationMessages"));
        assertThat(conflict.pointsDelta()).isEqualByComparingTo(jsonString(errorFixture, "pointsDelta"));
        assertThat(conflict.moneyImpact()).isEqualByComparingTo(jsonString(errorFixture, "moneyImpact"));
    }

    private static EvaluationRequest request() {
        return request("CA", "06037", null);
    }

    private static EvaluationRequest request(String state, String countyFips, String municipality) {
        return new EvaluationRequest(
            TENANT_ID,
            "QUOTE-PII-06-S05",
            "SCENARIO-PII-06-S05",
            "configured-product",
            "configured-investor",
            "configured-channel",
            "CONFIGURED_PURPOSE",
            "CONFIGURED_OCCUPANCY",
            "CONFIGURED_PROPERTY",
            Instant.parse("2026-02-01T00:00:00Z"),
            new BigDecimal("400000.00"),
            new BigDecimal("100.000000"),
            state,
            countyFips,
            municipality,
            "v1"
        );
    }

    private static JurisdictionAdjustmentRule rule() {
        return rule(RULE_ID, "CA", "06037", null, "POINTS_DELTA", "0.125000", null, null, 1);
    }

    private static JurisdictionAdjustmentRule rule(
        UUID ruleId,
        String state,
        String countyFips,
        String municipality,
        String method,
        String value,
        String cap,
        String floor,
        int priority
    ) {
        return JurisdictionAdjustmentRule.fromValues(
            TENANT_ID,
            RULE_BOOK_ID,
            ruleId,
            "v1",
            StateCode.of(state),
            countyFips,
            municipality,
            method,
            new BigDecimal(value),
            cap == null ? null : new BigDecimal(cap),
            floor == null ? null : new BigDecimal(floor),
            "CONFIGURED_STATE_REASON",
            "CONFIGURED_SOURCE_REF",
            priority,
            false,
            null,
            START,
            null,
            true
        );
    }

    private static JurisdictionAdjustmentRule ruleWithSelectors(
        UUID ruleId,
        String productId,
        String investorId,
        String channel,
        String loanPurpose,
        String occupancy,
        String propertyType,
        String minLoanAmount,
        String maxLoanAmount,
        String value
    ) {
        return JurisdictionAdjustmentRule.fromValuesWithSelectors(
            TENANT_ID,
            RULE_BOOK_ID,
            ruleId,
            "v1",
            StateCode.of("CA"),
            "06037",
            null,
            productId,
            investorId,
            channel,
            loanPurpose,
            occupancy,
            propertyType,
            minLoanAmount == null ? null : new BigDecimal(minLoanAmount),
            maxLoanAmount == null ? null : new BigDecimal(maxLoanAmount),
            "POINTS_DELTA",
            new BigDecimal(value),
            null,
            null,
            "CONFIGURED_STATE_REASON",
            "CONFIGURED_SOURCE_REF",
            1,
            false,
            null,
            START,
            null,
            true
        );
    }

    private static EvaluationRequest requestFromFixture(String json) {
        return new EvaluationRequest(
            UUID.fromString(jsonString(json, "tenantId")),
            jsonString(json, "quoteId"),
            jsonString(json, "scenarioId"),
            jsonString(json, "productId"),
            jsonString(json, "investorId"),
            jsonString(json, "channel"),
            jsonString(json, "loanPurpose"),
            jsonString(json, "occupancy"),
            jsonString(json, "propertyType"),
            Instant.parse(jsonString(json, "quoteDate")),
            new BigDecimal(jsonString(json, "loanAmount")),
            new BigDecimal("100.000000"),
            jsonString(json, "propertyState"),
            jsonString(json, "propertyCountyFips"),
            jsonStringOrNull(json, "propertyMunicipality"),
            jsonString(json, "ruleBookVersion")
        );
    }

    private static String readGoldenFixture(String fileName) throws Exception {
        return Files.readString(Path.of("golden", "PII-06-S05-state-adjustments", fileName));
    }

    private static String jsonString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        assertThat(matcher.find()).as("fixture key %s", key).isTrue();
        return matcher.group(1);
    }

    private static String jsonStringOrNull(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String jsonNumber(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        assertThat(matcher.find()).as("fixture numeric key %s", key).isTrue();
        return matcher.group(1);
    }

    private static String jsonArrayFirstString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        assertThat(matcher.find()).as("fixture array key %s", key).isTrue();
        return matcher.group(1);
    }
}
