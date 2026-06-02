package com.wcpe.eligibility;

import com.wcpe.eligibility.config.PropertyTypeRuleProperties;
import com.wcpe.eligibility.config.PropertyTypeRuleProperties.RuleRowConfig;
import com.wcpe.eligibility.config.PropertyTypeRuleProperties.RuleSetConfig;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.repository.PropertyTypeRuleRepository;
import com.wcpe.eligibility.service.PropertyTypeRuleService;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PropertyTypeRuleTest {

    private static final String PRODUCT = "CONF30";
    private static final String INVESTOR = "FNMA";
    private static final String CHANNEL = "RETAIL";
    private static final UUID TENANT = UUID.randomUUID();

    private PropertyTypeRuleService buildService(boolean withConfig) {
        if (!withConfig) {
            return new PropertyTypeRuleService(new PropertyTypeRuleProperties(), new PropertyTypeRuleRepository(null));
        }
        PropertyTypeRuleProperties props = buildDefaultProperties();
        return new PropertyTypeRuleService(props, new PropertyTypeRuleRepository(null));
    }

    private static PropertyTypeRuleProperties buildDefaultProperties() {
        PropertyTypeRuleProperties props = new PropertyTypeRuleProperties();
        props.setEnabled(true);

        RuleSetConfig set = new RuleSetConfig();
        set.setRuleSetId("aaaa1111-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        set.setProductFamily("CONVENTIONAL");
        set.setProductCode(PRODUCT);
        set.setInvestorCode(INVESTOR);
        set.setChannel(CHANNEL);
        set.setVersion(1);

        // Single family 1 unit - always allowed
        RuleRowConfig sfRule = new RuleRowConfig();
        sfRule.setRuleId("bbbb1111-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        sfRule.setPropertyType("SINGLE_FAMILY");
        sfRule.setUnitsMin(1);
        sfRule.setUnitsMax(1);
        sfRule.setLoanPurpose("PURCHASE");
        sfRule.setDecision("ALLOW");
        sfRule.setSeverity("PASS");
        sfRule.setReasonCode("SF_ALLOWED");
        sfRule.setMessageTemplate("Single family property type is allowed for {{productCode}}.");
        sfRule.setPriority(10);

        // Condo - condition (project review warning)
        RuleRowConfig condoRule = new RuleRowConfig();
        condoRule.setRuleId("bbbb2222-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        condoRule.setPropertyType("CONDO");
        condoRule.setUnitsMin(1);
        condoRule.setUnitsMax(1);
        condoRule.setLoanPurpose("PURCHASE");
        condoRule.setProjectReviewRequirement("WARNING");
        condoRule.setDecision("CONDITION");
        condoRule.setSeverity("CONDITION");
        condoRule.setReasonCode("CONDO_PROJECT_REVIEW_REQUIRED");
        condoRule.setMessageTemplate("Condo property type requires project review before final pricing.");
        condoRule.setPriority(20);

        // PUD - condition (project review warning)
        RuleRowConfig pudRule = new RuleRowConfig();
        pudRule.setRuleId("bbbb3333-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        pudRule.setPropertyType("PUD");
        pudRule.setUnitsMin(1);
        pudRule.setUnitsMax(1);
        pudRule.setLoanPurpose("PURCHASE");
        pudRule.setProjectReviewRequirement("WARNING");
        pudRule.setDecision("CONDITION");
        pudRule.setSeverity("CONDITION");
        pudRule.setReasonCode("PUD_PROJECT_REVIEW_REQUIRED");
        pudRule.setMessageTemplate("PUD property type requires project review before final pricing.");
        pudRule.setPriority(20);

        // 2-4 unit 1-2 units allowed
        RuleRowConfig multiRule = new RuleRowConfig();
        multiRule.setRuleId("bbbb4444-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        multiRule.setPropertyType("TWO_TO_FOUR_UNIT");
        multiRule.setUnitsMin(2);
        multiRule.setUnitsMax(4);
        multiRule.setLoanPurpose("PURCHASE");
        multiRule.setDecision("ALLOW");
        multiRule.setSeverity("PASS");
        multiRule.setReasonCode("MULTI_UNIT_ALLOWED");
        multiRule.setMessageTemplate("2-4 unit property type is allowed for {{productCode}}.");
        multiRule.setPriority(30);

        set.setRows(Arrays.asList(sfRule, condoRule, pudRule, multiRule));

        props.setRuleSets(Collections.singletonList(set));
        return props;
    }

    private static PropertyTypeEvaluationRequest buildEvalRequest(String propertyType, int units,
                                                                   String occupancyType,
                                                                   String loanPurpose,
                                                                   String projectReviewStatus) {
        return new PropertyTypeEvaluationRequest(
            UUID.randomUUID(),
            1,
            null,
            new PropertyTypeProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR, CHANNEL),
            new PropertyTypeFacts(propertyType, units, occupancyType, loanPurpose, projectReviewStatus)
        );
    }

    @Test
    void allowsSingleFamilyOneUnit() {
        PropertyTypeRuleService service = buildService(true);

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "SINGLE_FAMILY", 1, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertEquals("ELIGIBLE", result.decision().eligibilityStatus(),
            "Single family 1 unit should be eligible");
        assertEquals("PASS", result.decision().severity(),
            "Severity should be PASS for allowed single family");
        assertNotNull(result.resultHash(), "Result hash should be present");
        assertNotNull(result.evaluationId(), "Evaluation ID should be present");
        assertEquals("CONF_PROPERTY_TYPE", result.decision().ruleCode());
    }

    @Test
    void deniesManufacturedHomeWithoutExplicitRule() {
        PropertyTypeRuleService service = buildService(true);
        // Default config has no manufactured home rule

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "MANUFACTURED_HOME", 1, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertEquals("INELIGIBLE", result.decision().eligibilityStatus(),
            "Manufactured home should be denied without explicit rule");
        assertEquals("HARD_STOP", result.decision().severity(),
            "Severity should be HARD_STOP for denied manufactured home");
        assertEquals("MANUFACTURED_HOME_DENIED", result.decision().reasonCode(),
            "Reason code should indicate manufactured home denial");
        assertNotNull(result.decision().message(), "Should have explanation message");
    }

    @Test
    void warnsCondoUnknownProjectReview() {
        PropertyTypeRuleService service = buildService(true);

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "CONDO", 1, "PRIMARY_RESIDENCE", "PURCHASE", "UNKNOWN"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertEquals("WARNING", result.decision().eligibilityStatus(),
            "Condo with unknown project review should produce WARNING");
        assertEquals("CONDITION", result.decision().severity(),
            "Severity should be CONDITION for condo project review warning");
        assertEquals("CONDO_PROJECT_REVIEW_REQUIRED", result.decision().reasonCode(),
            "Reason code should reference condo project review");
        assertEquals("WARNING", result.decision().projectReviewRequirement(),
            "Project review requirement should still be WARNING");
    }

    @Test
    void rejectsInvalidUnitRange() {
        PropertyTypeRuleService service = buildService(true);

        // Units = 0 is invalid
        PropertyTypeEvaluationRequest requestZero = buildEvalRequest(
            "SINGLE_FAMILY", 0, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult resultZero = service.evaluate(TENANT, requestZero);

        assertEquals("INSUFFICIENT_DATA", resultZero.decision().eligibilityStatus(),
            "Zero units should produce insufficient data");
        assertEquals("INVALID_UNITS", resultZero.decision().reasonCode(),
            "Reason code should reference invalid units");

        // Units = 5 is invalid
        PropertyTypeEvaluationRequest requestFive = buildEvalRequest(
            "SINGLE_FAMILY", 5, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult resultFive = service.evaluate(TENANT, requestFive);

        assertEquals("INSUFFICIENT_DATA", resultFive.decision().eligibilityStatus(),
            "Five units should produce insufficient data");
        assertEquals("INVALID_UNITS", resultFive.decision().reasonCode(),
            "Reason code should reference invalid units");
    }

    @Test
    void missingPropertyTypeReturnsInsufficientData() {
        PropertyTypeRuleService service = buildService(true);

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            null, 1, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertEquals("INSUFFICIENT_DATA", result.decision().eligibilityStatus());
        assertEquals("MISSING_PROPERTY_TYPE", result.decision().reasonCode());
    }

    @Test
    void unknownPropertyTypeReturnsInsufficientData() {
        PropertyTypeRuleService service = buildService(true);

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "APARTMENT", 1, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertEquals("INSUFFICIENT_DATA", result.decision().eligibilityStatus());
        assertEquals("UNKNOWN_PROPERTY_TYPE", result.decision().reasonCode());
    }

    @Test
    void resultHashIsDeterministic() {
        PropertyTypeRuleService service = buildService(true);

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "SINGLE_FAMILY", 1, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertTrue(result.resultHash().startsWith("sha256:"),
            "Result hash should be sha256 prefixed");
    }

    @Test
    void allowsPudWithProjectReviewWarning() {
        PropertyTypeRuleService service = buildService(true);

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "PUD", 1, "PRIMARY_RESIDENCE", "PURCHASE", "UNKNOWN"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertEquals("WARNING", result.decision().eligibilityStatus());
        assertEquals("PUD_PROJECT_REVIEW_REQUIRED", result.decision().reasonCode());
    }

    @Test
    void deniesPropertyTypeNotInRuleset() {
        PropertyTypeRuleService service = buildService(true);
        // Manufactured home not in ruleset = denial

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "MANUFACTURED_HOME", 1, "SECONDARY", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertEquals("INELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("HARD_STOP", result.decision().severity());
    }

    @Test
    void noConfigNoDbReturnsCannotDecide() {
        PropertyTypeRuleService service = new PropertyTypeRuleService(
            new PropertyTypeRuleProperties(), new PropertyTypeRuleRepository(null));

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "SINGLE_FAMILY", 1, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertEquals("CANNOT_DECIDE", result.decision().eligibilityStatus(),
            "No config and no DB should yield CANNOT_DECIDE");
        assertEquals("PROPERTY_TYPE_RULE_NOT_CONFIGURED", result.decision().reasonCode());
    }

    @Test
    void messageTemplateResolvesPlaceholders() {
        PropertyTypeRuleService service = buildService(true);

        PropertyTypeEvaluationRequest request = buildEvalRequest(
            "SINGLE_FAMILY", 1, "PRIMARY_RESIDENCE", "PURCHASE", "NONE"
        );

        PropertyTypeEvaluationResult result = service.evaluate(TENANT, request);

        assertTrue(result.decision().message().contains("CONF30"),
            "Message should resolve productCode placeholder");
    }
}
