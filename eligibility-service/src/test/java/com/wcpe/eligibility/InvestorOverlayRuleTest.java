package com.wcpe.eligibility;

import com.wcpe.eligibility.config.InvestorOverlayRuleProperties;
import com.wcpe.eligibility.config.InvestorOverlayRuleProperties.ConditionConfig;
import com.wcpe.eligibility.config.InvestorOverlayRuleProperties.RuleRowConfig;
import com.wcpe.eligibility.config.InvestorOverlayRuleProperties.RuleSetConfig;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.repository.InvestorOverlayRuleRepository;
import com.wcpe.eligibility.service.InvestorOverlayRuleService;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InvestorOverlayRuleTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PRODUCT_VERSION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVESTOR = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void neverLoosensBaseIneligibleDecision() {
        InvestorOverlayEvaluationResult result = service(true).evaluate(TENANT, request("INELIGIBLE", Map.of(
            "representativeFico", 720,
            "ltv", "0.70000",
            "occupancyType", "INVESTMENT_PROPERTY"
        )));

        assertEquals("INELIGIBLE", result.overlaySummaryStatus());
        assertEquals("BASE_DECISION_NOT_ELIGIBLE", result.decisions().get(0).reasonCode());
    }

    @Test
    void evaluatesMinFicoConditionalOnLtvAndOccupancy() {
        InvestorOverlayEvaluationResult result = service(true).evaluate(TENANT, request("ELIGIBLE", Map.of(
            "representativeFico", 690,
            "ltv", "0.80000",
            "occupancyType", "INVESTMENT_PROPERTY"
        )));

        assertEquals("INELIGIBLE", result.overlaySummaryStatus());
        InvestorOverlayDecision decision = result.decisions().get(0);
        assertEquals("INV_MIN_FICO_BY_LTV_OCC", decision.ruleCode());
        assertEquals("INVESTOR_MIN_FICO_NOT_MET", decision.reasonCode());
        assertEquals("690", decision.actualValue());
        assertEquals("700", decision.thresholdValue());
        assertTrue(result.resultHash().startsWith("sha256:"));

        InvestorOverlayEvaluationResult notApplicable = service(true).evaluate(TENANT, request("ELIGIBLE", Map.of(
            "representativeFico", 690,
            "ltv", "0.70000",
            "occupancyType", "INVESTMENT_PROPERTY"
        )));
        assertEquals("ELIGIBLE", notApplicable.overlaySummaryStatus());
        assertEquals("OVERLAY_CONDITION_NOT_APPLICABLE", notApplicable.decisions().get(0).reasonCode());
    }

    @Test
    void rejectsUnsupportedFactPath() {
        InvestorOverlayRuleProperties props = buildProperties();
        props.getRuleSets().get(0).getRows().get(0).setFactPath("borrowerRace");

        InvestorOverlayEvaluationResult result = new InvestorOverlayRuleService(props, new InvestorOverlayRuleRepository(null))
            .evaluate(TENANT, request("ELIGIBLE", Map.of("borrowerRace", "ANY")));

        assertEquals("INELIGIBLE", result.overlaySummaryStatus());
        assertEquals("UNSUPPORTED_OVERLAY_FACT_PATH", result.decisions().get(0).reasonCode());
    }

    @Test
    void rejectsUnsupportedOperator() {
        InvestorOverlayRuleProperties props = buildProperties();
        props.getRuleSets().get(0).getRows().get(0).setOperator("LOOSEN");

        InvestorOverlayEvaluationResult result = new InvestorOverlayRuleService(props, new InvestorOverlayRuleRepository(null))
            .evaluate(TENANT, request("ELIGIBLE", Map.of("representativeFico", 690)));

        assertEquals("UNSUPPORTED_OVERLAY_OPERATOR", result.decisions().get(0).reasonCode());
    }

    @Test
    void samePrecedenceConflictFailsClosed() {
        InvestorOverlayRuleProperties props = buildProperties();
        RuleSetConfig second = buildProperties().getRuleSets().get(0);
        second.setOverlaySetId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        props.getRuleSets().add(second);

        InvestorOverlayEvaluationResult result = new InvestorOverlayRuleService(props, new InvestorOverlayRuleRepository(null))
            .evaluate(TENANT, request("ELIGIBLE", Map.of("representativeFico", 690)));

        assertEquals("OVERLAY_CONFLICT", result.decisions().get(0).reasonCode());
    }

    @Test
    void resultHashIsDeterministicWithoutEvaluationId() {
        InvestorOverlayRuleService service = service(true);
        InvestorOverlayEvaluationRequest request = request("ELIGIBLE", Map.of("representativeFico", 690));

        String first = service.evaluate(TENANT, request).resultHash();
        String second = service.evaluate(TENANT, request).resultHash();

        assertEquals(first, second);
    }

    @Test
    void mapsDatabaseBackedConditionExpressionJson() {
        InvestorOverlayRuleRepository repository = new InvestorOverlayRuleRepository(null);

        List<InvestorOverlayRuleRow.Condition> conditions = repository.parseConditions("[{\"fact_path\":\"ltv\",\"operator\":\"GTE\",\"comparison_value\":\"0.75001\"}]");

        assertEquals(1, conditions.size());
        assertEquals("ltv", conditions.get(0).factPath());
        assertEquals("GTE", conditions.get(0).operator());
        assertEquals("0.75001", conditions.get(0).comparisonValue());
    }

    private InvestorOverlayRuleService service(boolean withConfig) {
        return new InvestorOverlayRuleService(withConfig ? buildProperties() : new InvestorOverlayRuleProperties(), new InvestorOverlayRuleRepository(null));
    }

    private InvestorOverlayEvaluationRequest request(String baseStatus, Map<String, Object> facts) {
        return new InvestorOverlayEvaluationRequest(
            UUID.randomUUID(), 1, "2026-05-13", baseStatus,
            new InvestorOverlayProductCandidate(PRODUCT_VERSION, "CONF30", INVESTOR, "ABC", "RETAIL"), facts
        );
    }

    private InvestorOverlayRuleProperties buildProperties() {
        InvestorOverlayRuleProperties props = new InvestorOverlayRuleProperties();
        props.setEnabled(true);
        RuleSetConfig set = new RuleSetConfig();
        set.setOverlaySetId("ffffffff-ffff-ffff-ffff-ffffffffffff");
        set.setInvestorId(INVESTOR.toString());
        set.setProductVersionId(PRODUCT_VERSION.toString());
        set.setChannel("RETAIL");
        set.setVersion(3);

        RuleRowConfig minFico = new RuleRowConfig();
        minFico.setOverlayRuleId("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        minFico.setRuleCode("INV_MIN_FICO_BY_LTV_OCC");
        minFico.setFactPath("representativeFico");
        minFico.setOperator("GTE");
        minFico.setComparisonValue("700");
        minFico.setValueType("INTEGER");
        ConditionConfig ltvCondition = new ConditionConfig();
        ltvCondition.setFactPath("ltv");
        ltvCondition.setOperator("GTE");
        ltvCondition.setComparisonValue("0.75001");
        ConditionConfig occupancyCondition = new ConditionConfig();
        occupancyCondition.setFactPath("occupancyType");
        occupancyCondition.setOperator("EQ");
        occupancyCondition.setComparisonValue("INVESTMENT_PROPERTY");
        minFico.setConditions(List.of(ltvCondition, occupancyCondition));
        minFico.setSeverity("HARD_STOP");
        minFico.setReasonCode("INVESTOR_MIN_FICO_NOT_MET");
        minFico.setMessageTemplate("Configured investor overlay requires at least {{thresholdValue}}; actual {{actualValue}}.");
        minFico.setPriority(10);
        set.setRows(List.of(minFico));
        props.setRuleSets(new ArrayList<>(List.of(set)));
        return props;
    }
}
