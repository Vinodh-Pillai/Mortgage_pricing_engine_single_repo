package com.wcpe.eligibility;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.domain.rules.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RuleBoundaryTest {
    private static FicoMinimumRule ficoRule;
    private static LtvRule ltvRule;
    private static DtiRule dtiRule;
    private static final String PRODUCT = "CONV30";
    private static final String INVESTOR = "FNMA";

    @BeforeAll
    static void setup() {
        ficoRule = new FicoMinimumRule();
        ltvRule = new LtvRule();
        dtiRule = new DtiRule();
    }

    @Test void fico_619_ineligible() {
        EligibilityRequest req = buildRequest(619, new BigDecimal("8500"), new BigDecimal("1200"), new BigDecimal("400000"), new BigDecimal("500000"));
        RuleDecision d = ficoRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("HARD_STOP", d.severity());
        assertEquals("INELIGIBLE", d.status());
        assertEquals("FC01", d.reasonCode());
    }

    @Test void fico_620_eligible() {
        EligibilityRequest req = buildRequest(620, new BigDecimal("8500"), new BigDecimal("1200"), new BigDecimal("400000"), new BigDecimal("500000"));
        RuleDecision d = ficoRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());
        assertEquals("ELIGIBLE", d.status());
        assertNull(d.reasonCode());
    }

    @Test void fico_621_eligible() {
        EligibilityRequest req = buildRequest(621, new BigDecimal("8500"), new BigDecimal("1200"), new BigDecimal("400000"), new BigDecimal("500000"));
        RuleDecision d = ficoRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());
        assertEquals("ELIGIBLE", d.status());
    }

    @Test void fico_null_warning() {
        EligibilityRequest req = buildRequest(null, new BigDecimal("8500"), new BigDecimal("1200"), new BigDecimal("400000"), new BigDecimal("500000"));
        RuleDecision d = ficoRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("WARNING", d.severity());
        assertEquals("INSUFFICIENT_DATA", d.status());
        assertEquals("FC01", d.reasonCode());
    }

    @Test void ltv_096_eligible() {
        BigDecimal price = new BigDecimal("400000");
        BigDecimal loan = new BigDecimal("384000");
        EligibilityRequest req = new EligibilityRequest(
            new BorrowerProfile(740, new BigDecimal("8500"), new BigDecimal("1200")),
            new PropertyProfile("CA", "Los Angeles", "90001", "SINGLE_FAMILY", 1, "PRIMARY", price, price),
            new LoanProfile("PURCHASE", loan, BigDecimal.ZERO, 45, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR)
        );
        RuleDecision d = ltvRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());
        assertEquals("ELIGIBLE", d.status());
    }

    @Test void ltv_097_exactly_eligible() {
        BigDecimal price = new BigDecimal("400000");
        BigDecimal loan = new BigDecimal("388000");
        EligibilityRequest req = new EligibilityRequest(
            new BorrowerProfile(740, new BigDecimal("8500"), new BigDecimal("1200")),
            new PropertyProfile("CA", "Los Angeles", "90001", "SINGLE_FAMILY", 1, "PRIMARY", price, price),
            new LoanProfile("PURCHASE", loan, BigDecimal.ZERO, 45, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR)
        );
        RuleDecision d = ltvRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());
        assertEquals("ELIGIBLE", d.status());
    }

    @Test void ltv_098_ineligible() {
        BigDecimal price = new BigDecimal("400000");
        BigDecimal loan = new BigDecimal("392000");
        EligibilityRequest req = new EligibilityRequest(
            new BorrowerProfile(740, new BigDecimal("8500"), new BigDecimal("1200")),
            new PropertyProfile("CA", "Los Angeles", "90001", "SINGLE_FAMILY", 1, "PRIMARY", price, price),
            new LoanProfile("PURCHASE", loan, BigDecimal.ZERO, 45, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR)
        );
        RuleDecision d = ltvRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("HARD_STOP", d.severity());
        assertEquals("INELIGIBLE", d.status());
    }

    @Test void dti_042_eligible() {
        BigDecimal income = new BigDecimal("1000");
        BigDecimal debt = new BigDecimal("420");
        EligibilityRequest req = new EligibilityRequest(
            new BorrowerProfile(740, income, debt),
            new PropertyProfile("CA", "Los Angeles", "90001", "SINGLE_FAMILY", 1, "PRIMARY", new BigDecimal("300000"), new BigDecimal("300000")),
            new LoanProfile("PURCHASE", new BigDecimal("240000"), BigDecimal.ZERO, 45, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR)
        );
        RuleDecision d = dtiRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());
        assertEquals("ELIGIBLE", d.status());
    }

    @Test void dti_043_exactly_eligible() {
        BigDecimal income = new BigDecimal("1000");
        BigDecimal debt = new BigDecimal("430");
        EligibilityRequest req = new EligibilityRequest(
            new BorrowerProfile(740, income, debt),
            new PropertyProfile("CA", "Los Angeles", "90001", "SINGLE_FAMILY", 1, "PRIMARY", new BigDecimal("300000"), new BigDecimal("300000")),
            new LoanProfile("PURCHASE", new BigDecimal("240000"), BigDecimal.ZERO, 45, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR)
        );
        RuleDecision d = dtiRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());
        assertEquals("ELIGIBLE", d.status());
    }

    @Test void dti_044_ineligible() {
        BigDecimal income = new BigDecimal("1000");
        BigDecimal debt = new BigDecimal("440");
        EligibilityRequest req = new EligibilityRequest(
            new BorrowerProfile(740, income, debt),
            new PropertyProfile("CA", "Los Angeles", "90001", "SINGLE_FAMILY", 1, "PRIMARY", new BigDecimal("300000"), new BigDecimal("300000")),
            new LoanProfile("PURCHASE", new BigDecimal("240000"), BigDecimal.ZERO, 45, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR)
        );
        RuleDecision d = dtiRule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("HARD_STOP", d.severity());
        assertEquals("INELIGIBLE", d.status());
    }

    private EligibilityRequest buildRequest(Integer fico, BigDecimal income, BigDecimal debt, BigDecimal loanAmount, BigDecimal price) {
        return new EligibilityRequest(
            new BorrowerProfile(fico, income, debt),
            new PropertyProfile("CA", "Los Angeles", "90001", "SINGLE_FAMILY", 1, "PRIMARY", price, price),
            new LoanProfile("PURCHASE", loanAmount, BigDecimal.ZERO, 45, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR)
        );
    }
}
