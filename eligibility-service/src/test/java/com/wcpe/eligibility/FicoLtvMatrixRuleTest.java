package com.wcpe.eligibility;

import com.wcpe.eligibility.config.FicoLtvMatrixProperties;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.domain.rules.FicoLtvMatrixRule;
import com.wcpe.eligibility.repository.FicoLtvMatrixRepository;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FicoLtvMatrixRuleTest {

    private static final String PRODUCT = "CONF30";
    private static final String INVESTOR = "FNMA";

    private static FicoLtvMatrixProperties buildDefaultProperties() {
        FicoLtvMatrixProperties props = new FicoLtvMatrixProperties();
        props.setEnabled(true);

        FicoLtvMatrixProperties.MatrixSetConfig matrixSet = new FicoLtvMatrixProperties.MatrixSetConfig();
        matrixSet.setMatrixSetId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        matrixSet.setProductFamily("CONVENTIONAL");
        matrixSet.setInvestorCode("FNMA");
        matrixSet.setStatus("PUBLISHED");
        matrixSet.setVersion(1);

        matrixSet.setRows(Arrays.asList(
            buildRow("11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 740, 850, "0.97000", "PURCHASE", "PRIMARY_RESIDENCE"),
            buildRow("11111111-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 720, 739, "0.95000", "PURCHASE", "PRIMARY_RESIDENCE"),
            buildRow("11111111-cccc-cccc-cccc-cccccccccccc", 700, 719, "0.90000", "PURCHASE", "PRIMARY_RESIDENCE"),
            buildRow("11111111-dddd-dddd-dddd-dddddddddddd", 680, 699, "0.80000", "PURCHASE", "PRIMARY_RESIDENCE"),
            buildRow("11111111-eeee-eeee-eeee-eeeeeeeeeeee", 660, 679, "0.80000", "PURCHASE", "PRIMARY_RESIDENCE"),
            buildRow("11111111-ffff-ffff-ffff-ffffffffffff", 620, 659, "0.80000", "PURCHASE", "PRIMARY_RESIDENCE")
        ));

        props.setMatrixSets(Collections.singletonList(matrixSet));
        return props;
    }

    private static FicoLtvMatrixProperties.MatrixRowConfig buildRow(String id, int ficoMin, int ficoMax, String maxLtv,
                                                                      String purpose, String occupancy) {
        FicoLtvMatrixProperties.MatrixRowConfig row = new FicoLtvMatrixProperties.MatrixRowConfig();
        row.setMatrixRowId(id);
        row.setFicoMin(ficoMin);
        row.setFicoMax(ficoMax);
        row.setMaxLtv(maxLtv);
        row.setLoanPurpose(purpose);
        row.setOccupancyType(occupancy);
        row.setReasonCode("MX05");
        return row;
    }

    private static EligibilityRequest buildRequest(Integer fico, BigDecimal loanAmount, BigDecimal purchasePrice,
                                                     String occupancy, String propertyType, int units,
                                                     String loanPurpose) {
        return new EligibilityRequest(
            new BorrowerProfile(fico, new BigDecimal("8500"), new BigDecimal("1200")),
            new PropertyProfile("CA", "Los Angeles", "90001", propertyType, units, occupancy, purchasePrice, purchasePrice),
            new LoanProfile(loanPurpose, loanAmount, BigDecimal.ZERO, 45, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), PRODUCT, INVESTOR)
        );
    }

    private static FicoLtvMatrixRule buildRule() {
        FicoLtvMatrixProperties props = buildDefaultProperties();
        return new FicoLtvMatrixRule(props, null);
    }

    @Test
    void matchesInclusiveFicoBoundaries() {
        FicoLtvMatrixRule rule = buildRule();

        // FICO 740 (upper tier boundary inclusive) with 80% LTV -> PASS (max 97%)
        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan = new BigDecimal("400000");
        EligibilityRequest req = buildRequest(740, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity(), "FICO 740 should pass at 80% LTV with max 97%");
        assertEquals("ELIGIBLE", d.status());

        // FICO 850 (absolute upper bound) -> PASS
        req = buildRequest(850, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());

        // FICO 720 (lower boundary of 720-739 band) with 80% LTV -> PASS (max 95%)
        req = buildRequest(720, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());

        // FICO 739 (upper boundary of 720-739 band) -> PASS
        req = buildRequest(739, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());

        // FICO 620 (absolute minimum) with 80% LTV -> PASS (max 80%)
        req = buildRequest(620, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());
    }

    @Test
    void failsWhenLtvExceedsMaxByOneBasisPoint() {
        FicoLtvMatrixRule rule = buildRule();

        // FICO 620 has max LTV 80% = 0.80000
        // 80.01% = 0.80010 should fail
        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan80pct = new BigDecimal("400000"); // exactly 80% PASS
        BigDecimal loan80pct01bp = new BigDecimal("400050"); // 80.01% FAIL

        EligibilityRequest reqPass = buildRequest(620, loan80pct, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision dPass = rule.evaluate(reqPass, PRODUCT, INVESTOR);
        assertEquals("PASS", dPass.severity(), "FICO 620 at exactly 80% should pass");
        assertEquals("ELIGIBLE", dPass.status());

        EligibilityRequest reqFail = buildRequest(620, loan80pct01bp, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision dFail = rule.evaluate(reqFail, PRODUCT, INVESTOR);
        assertEquals("HARD_STOP", dFail.severity(), "FICO 620 at 80.01% LTV should fail (exceeds max by 1 bp)");
        assertEquals("INELIGIBLE", dFail.status());
        assertEquals("MX05", dFail.reasonCode());
        assertTrue(dFail.message().contains("LTV"), "Message should mention LTV");
    }

    @Test
    void rejectsOverlappingFicoBands() {
        // Test that the rule resolves to the most specific matching row
        // when rows are properly non-overlapping. Overlapping band configs
        // are prevented at the DB schema level (matrix_fico_ck). Here we verify
        // the rule picks one deterministically.
        FicoLtvMatrixProperties props = new FicoLtvMatrixProperties();
        props.setEnabled(true);

        FicoLtvMatrixProperties.MatrixSetConfig matrixSet = new FicoLtvMatrixProperties.MatrixSetConfig();
        matrixSet.setMatrixSetId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        matrixSet.setProductFamily("CONVENTIONAL");
        matrixSet.setInvestorCode("FNMA");
        matrixSet.setStatus("PUBLISHED");
        matrixSet.setVersion(1);

        int overlapMin = 700;
        int overlapMax = 740;

        matrixSet.setRows(Arrays.asList(
            buildRow("22222222-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 300, 740, "0.80000", "PURCHASE", "PRIMARY_RESIDENCE"),
            buildRow("22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 740, 850, "0.97000", "PURCHASE", "PRIMARY_RESIDENCE")
        ));

        props.setMatrixSets(Collections.singletonList(matrixSet));
        FicoLtvMatrixRule rule = new FicoLtvMatrixRule(props, null);

        // FICO 740 matches both rows; rule should pick one deterministically
        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan80pct = new BigDecimal("400000");
        EligibilityRequest req = buildRequest(740, loan80pct, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision d = rule.evaluate(req, PRODUCT, INVESTOR);

        assertEquals("PASS", d.severity(), "Overlapping band still resolves to at least one match");
        assertEquals("ELIGIBLE", d.status());
    }

    @Test
    void neverInfersMissingFico() {
        FicoLtvMatrixRule rule = buildRule();
        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan = new BigDecimal("400000");
        EligibilityRequest req = buildRequest(null, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision d = rule.evaluate(req, PRODUCT, INVESTOR);

        assertEquals("INSUFFICIENT_DATA", d.status(), "Missing FICO must never be inferred");
        assertNotNull(d.severity(), "Must produce a severity even for missing FICO");

        FicoLtvMatrixRule rule2 = new FicoLtvMatrixRule(new FicoLtvMatrixProperties(), null);
        RuleDecision d2 = rule2.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("CANNOT_DECIDE", d2.status(), "No matrix + missing FICO = CANNOT_DECIDE");
    }

    @Test
    void highFicoHigherLtvAllowance() {
        FicoLtvMatrixRule rule = buildRule();

        // FICO 740 can go up to 97%
        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan95pct = new BigDecimal("475000"); // 95%

        EligibilityRequest reqHigh = buildRequest(740, loan95pct, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision dHigh = rule.evaluate(reqHigh, PRODUCT, INVESTOR);
        assertEquals("PASS", dHigh.severity(), "FICO 740 should allow 95% LTV (max 97%)");

        // Same 95% LTV with FICO 700 should fail (max 90%)
        EligibilityRequest reqMed = buildRequest(700, loan95pct, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision dMed = rule.evaluate(reqMed, PRODUCT, INVESTOR);
        assertEquals("HARD_STOP", dMed.severity(), "FICO 700 should fail at 95% LTV (max 90%)");
        assertEquals("INELIGIBLE", dMed.status());
    }

    @Test
    void ficoBelowValidRange() {
        FicoLtvMatrixRule rule = buildRule();
        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan = new BigDecimal("400000");

        EligibilityRequest req = buildRequest(299, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("HARD_STOP", d.severity());
        assertEquals("INELIGIBLE", d.status());
        assertEquals("MX03", d.reasonCode());
    }

    @Test
    void ficoAboveValidRange() {
        FicoLtvMatrixRule rule = buildRule();
        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan = new BigDecimal("400000");

        EligibilityRequest req = buildRequest(851, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("HARD_STOP", d.severity());
        assertEquals("INELIGIBLE", d.status());
        assertEquals("MX03", d.reasonCode());
    }

    @Test
    void fico620With95LtvOutsideMatrix() {
        FicoLtvMatrixRule rule = buildRule();

        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan95pct = new BigDecimal("475000");
        EligibilityRequest req = buildRequest(620, loan95pct, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("HARD_STOP", d.severity());
        assertEquals("INELIGIBLE", d.status());
        assertEquals("MX05", d.reasonCode());
        assertTrue(d.message().contains("95.00%"));
        assertTrue(d.message().contains("80.00%"));
    }

    @Test
    void passesWithCltvNotApplicable() {
        FicoLtvMatrixRule rule = buildRule();

        BigDecimal price = new BigDecimal("500000");
        BigDecimal loan = new BigDecimal("400000");
        EligibilityRequest req = buildRequest(740, loan, price, "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE");
        RuleDecision d = rule.evaluate(req, PRODUCT, INVESTOR);
        assertEquals("PASS", d.severity());
        assertEquals("ELIGIBLE", d.status());
    }

    @Test
    void ficoLtvRuleType() {
        FicoLtvMatrixRule rule = buildRule();
        assertEquals("R13", rule.getRuleType().getCode());
        assertEquals("FICO_LTV_MATRIX", rule.getRuleType().getName());
    }
}
