package com.wcpe.eligibility;

import com.wcpe.eligibility.config.OccupancyPurposeRuleProperties;
import com.wcpe.eligibility.config.OccupancyPurposeRuleProperties.RuleRowConfig;
import com.wcpe.eligibility.config.OccupancyPurposeRuleProperties.RuleSetConfig;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.repository.OccupancyPurposeRuleRepository;
import com.wcpe.eligibility.service.OccupancyPurposeRuleService;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OccupancyPurposeRuleTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT = "CONF30";
    private static final String INVESTOR = "FNMA";
    private static final String CHANNEL = "RETAIL";

    @Test
    void allowsPrimaryPurchase() {
        OccupancyPurposeRuleService service = serviceWith(ruleSet(50,
            row("PRIMARY_RESIDENCE", "ALLOW", "PASS", "PRIMARY_PURCHASE_ALLOWED", 10,
                "Primary residence purchase is allowed for {{productCode}}/{{investorCode}}/{{channel}}.")));

        OccupancyPurposeEvaluationResult result = service.evaluate(TENANT, request("PURCHASE", "PRIMARY_RESIDENCE", 1));

        assertEquals("ELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("PASS", result.decision().severity());
        assertEquals("PRIMARY_PURCHASE_ALLOWED", result.decision().reasonCode());
        assertTrue(result.resultHash().startsWith("sha256:"));
        assertEquals(71, result.resultHash().length());
    }

    @Test
    void deniesConfiguredInvestmentProperty() {
        OccupancyPurposeRuleService service = serviceWith(ruleSet(50,
            row("INVESTMENT_PROPERTY", "DENY", "HARD_STOP", "OCCUPANCY_NOT_ALLOWED_FOR_PRODUCT_CHANNEL", 10,
                "Investment property purchase is not allowed for {{productCode}}/{{investorCode}}/{{channel}}.")));

        OccupancyPurposeEvaluationResult result = service.evaluate(TENANT, request("PURCHASE", "INVESTMENT_PROPERTY", 1));

        assertEquals("INELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("HARD_STOP", result.decision().severity());
        assertEquals("OCCUPANCY_NOT_ALLOWED_FOR_PRODUCT_CHANNEL", result.decision().reasonCode());
        assertEquals("Investment property purchase is not allowed for CONF30/FNMA/RETAIL.", result.decision().message());
    }

    @Test
    void productOverrideBeatsGenericRule() {
        RuleSetConfig genericDeny = ruleSet(10,
            row("PRIMARY_RESIDENCE", "DENY", "HARD_STOP", "GENERIC_DENY", 10,
                "Generic conventional rule denies {{occupancyType}}."));
        genericDeny.setProductCode(null);
        genericDeny.setInvestorCode(null);
        genericDeny.setChannel(null);

        RuleSetConfig productAllow = ruleSet(50,
            row("PRIMARY_RESIDENCE", "ALLOW", "PASS", "PRODUCT_OVERRIDE_ALLOW", 10,
                "Product override allows {{occupancyType}}."));

        OccupancyPurposeRuleService service = serviceWith(genericDeny, productAllow);

        OccupancyPurposeEvaluationResult result = service.evaluate(TENANT, request("PURCHASE", "PRIMARY_RESIDENCE", 1));

        assertEquals("ELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("PRODUCT_OVERRIDE_ALLOW", result.decision().reasonCode());
        assertEquals("product_investor", result.decision().precedence());
    }

    @Test
    void samePriorityConflictFailsClosed() {
        OccupancyPurposeRuleService service = serviceWith(ruleSet(50,
            row("PRIMARY_RESIDENCE", "ALLOW", "PASS", "ALLOW_A", 10, "Allow."),
            row("PRIMARY_RESIDENCE", "DENY", "HARD_STOP", "DENY_A", 10, "Deny.")));

        OccupancyPurposeEvaluationResult result = service.evaluate(TENANT, request("PURCHASE", "PRIMARY_RESIDENCE", 1));

        assertEquals("INELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("HARD_STOP", result.decision().severity());
        assertEquals("OVERLAPPING_RULE_VERSION", result.decision().reasonCode());
        assertTrue(result.decision().message().contains("Ambiguous occupancy-purpose configuration"));
    }

    @Test
    void missingConfigurationFailsClosed() {
        OccupancyPurposeRuleService service = serviceWith();

        OccupancyPurposeEvaluationResult result = service.evaluate(TENANT, request("PURCHASE", "PRIMARY_RESIDENCE", 1));

        assertEquals("INELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("HARD_STOP", result.decision().severity());
        assertEquals("OCCUPANCY_PURPOSE_RULE_NOT_CONFIGURED", result.decision().reasonCode());
    }

    @Test
    void unsupportedPurposeFailsBeforeRuleEvaluation() {
        OccupancyPurposeRuleService service = serviceWith(ruleSet(50,
            row("PRIMARY_RESIDENCE", "ALLOW", "PASS", "PRIMARY_PURCHASE_ALLOWED", 10, "Allowed.")));

        OccupancyPurposeEvaluationResult result = service.evaluate(TENANT, request("REFINANCE", "PRIMARY_RESIDENCE", 1));

        assertEquals("INELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("UNSUPPORTED_LOAN_PURPOSE", result.decision().reasonCode());
    }

    @Test
    void resultHashIsDeterministicWithoutEvaluationId() {
        OccupancyPurposeRuleService service = serviceWith(ruleSet(50,
            row("PRIMARY_RESIDENCE", "ALLOW", "PASS", "PRIMARY_PURCHASE_ALLOWED", 10, "Allowed.")));
        OccupancyPurposeEvaluationRequest request = request("PURCHASE", "PRIMARY_RESIDENCE", 1);

        OccupancyPurposeEvaluationResult first = service.evaluate(TENANT, request);
        OccupancyPurposeEvaluationResult second = service.evaluate(TENANT, request);

        assertNotEquals(first.evaluationId(), second.evaluationId(), "Each evaluation keeps its own random audit id");
        assertEquals(first.resultHash(), second.resultHash(), "Replay hash must not include random evaluationId");
    }

    @Test
    void dbLoadedSpecificRuleBeatsNewerGenericVersion() {
        RuleSetConfig genericDeny = ruleSet(
            OccupancyPurposeRuleRepository.specificityPrecedence("CONVENTIONAL", null, null, null, null, null, 99),
            row("PRIMARY_RESIDENCE", "DENY", "HARD_STOP", "GENERIC_DENY", 10,
                "Newer generic conventional rule denies {{occupancyType}}."));
        genericDeny.setProductCode(null);
        genericDeny.setInvestorCode(null);
        genericDeny.setChannel(null);

        RuleSetConfig specificAllow = ruleSet(
            OccupancyPurposeRuleRepository.specificityPrecedence("CONVENTIONAL", PRODUCT,
                UUID.fromString("11111111-1111-1111-1111-111111111111"), INVESTOR, null, CHANNEL, 1),
            row("PRIMARY_RESIDENCE", "ALLOW", "PASS", "SPECIFIC_ALLOW", 10,
                "Specific product investor channel rule allows {{occupancyType}}."));

        OccupancyPurposeRuleService service = serviceWithRepository(new StubRepository(List.of(genericDeny, specificAllow)));

        OccupancyPurposeEvaluationResult result = service.evaluate(TENANT, request("PURCHASE", "PRIMARY_RESIDENCE", 1));

        assertEquals("ELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("SPECIFIC_ALLOW", result.decision().reasonCode());
        assertTrue(specificAllow.getPrecedence() > genericDeny.getPrecedence(),
            "Specificity must be ordered before version tie-break");
    }

    private OccupancyPurposeRuleService serviceWith(RuleSetConfig... ruleSets) {
        OccupancyPurposeRuleProperties props = new OccupancyPurposeRuleProperties();
        props.setEnabled(true);
        props.setRuleSets(List.of(ruleSets));
        return new OccupancyPurposeRuleService(props, new OccupancyPurposeRuleRepository(null));
    }

    private OccupancyPurposeRuleService serviceWithRepository(OccupancyPurposeRuleRepository repository) {
        return new OccupancyPurposeRuleService(new OccupancyPurposeRuleProperties(), repository);
    }

    private RuleSetConfig ruleSet(int precedence, RuleRowConfig... rows) {
        RuleSetConfig set = new RuleSetConfig();
        set.setRuleSetId(UUID.randomUUID().toString());
        set.setProductFamily("CONVENTIONAL");
        set.setProductCode(PRODUCT);
        set.setInvestorCode(INVESTOR);
        set.setChannel(CHANNEL);
        set.setPrecedence(precedence);
        set.setRows(List.of(rows));
        return set;
    }

    private RuleRowConfig row(String occupancyType, String decision, String severity, String reasonCode,
                              int priority, String messageTemplate) {
        RuleRowConfig row = new RuleRowConfig();
        row.setRuleId(UUID.randomUUID().toString());
        row.setLoanPurpose("PURCHASE");
        row.setOccupancyType(occupancyType);
        row.setPropertyType("SINGLE_FAMILY");
        row.setUnitsMin(1);
        row.setUnitsMax(4);
        row.setDecision(decision);
        row.setSeverity(severity);
        row.setReasonCode(reasonCode);
        row.setMessageTemplate(messageTemplate);
        row.setPriority(priority);
        return row;
    }

    private OccupancyPurposeEvaluationRequest request(String loanPurpose, String occupancyType, int units) {
        return new OccupancyPurposeEvaluationRequest(
            UUID.randomUUID(),
            1,
            "2026-05-13",
            new OccupancyPurposeProductCandidate(UUID.fromString("11111111-1111-1111-1111-111111111111"), PRODUCT, INVESTOR, CHANNEL),
            new OccupancyPurposeFacts(loanPurpose, occupancyType, units, "SINGLE_FAMILY")
        );
    }

    private static class StubRepository extends OccupancyPurposeRuleRepository {
        private final List<OccupancyPurposeRuleSetConfig> ruleSets;

        StubRepository(List<RuleSetConfig> ruleSets) {
            super(null);
            this.ruleSets = ruleSets.stream()
                .map(set -> new OccupancyPurposeRuleSetConfig(
                    UUID.fromString(set.getRuleSetId()),
                    set.getProductFamily(),
                    set.getInvestorCode() == null ? "" : set.getInvestorCode(),
                    set.getChannel(),
                    set.getPrecedence(),
                    set.getRows().stream().map(row -> new OccupancyPurposeRuleRow(
                        UUID.fromString(row.getRuleId()),
                        UUID.fromString(set.getRuleSetId()),
                        row.getLoanPurpose(),
                        row.getOccupancyType(),
                        row.getPropertyType(),
                        row.getUnitsMin(),
                        row.getUnitsMax(),
                        row.getDecision(),
                        row.getSeverity(),
                        row.getReasonCode(),
                        row.getMessageTemplate(),
                        row.getPriority()
                    )).toList()
                ))
                .toList();
        }

        @Override
        public List<OccupancyPurposeRuleSetConfig> resolve(UUID tenantId, String productCode, String investorCode,
                                                           String channel, Date effectiveDate) {
            return ruleSets;
        }
    }
}
