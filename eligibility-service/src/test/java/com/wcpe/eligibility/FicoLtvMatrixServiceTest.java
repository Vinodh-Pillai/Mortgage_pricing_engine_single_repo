package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.repository.FicoLtvMatrixRepository;
import com.wcpe.eligibility.service.FicoLtvMatrixService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FicoLtvMatrixServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MATRIX_SET = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID HIGH_ROW = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID LOW_ROW = UUID.fromString("55555555-5555-5555-5555-555555555558");

    @Test
    void evaluatesConfiguredMatrixAndReturnsReplayablePass() {
        FicoLtvEvaluationResult result = service(rows()).evaluate(TENANT, request(742, "0.80000"), "corr-1");

        assertEquals("ELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("PASS", result.decision().severity());
        assertEquals("FICO_LTV_WITHIN_MATRIX", result.decision().reasonCode());
        assertEquals(HIGH_ROW, result.decision().matchedRowId());
        assertEquals(new BigDecimal("0.97000"), result.decision().maxLtv());
        assertTrue(result.resultHash().startsWith("sha256:"));
    }

    @Test
    void failsWhenLtvExceedsConfiguredMaximum() {
        FicoLtvEvaluationResult result = service(rows()).evaluate(TENANT, request(620, "0.95000"), "corr-2");

        assertEquals("INELIGIBLE", result.decision().eligibilityStatus());
        assertEquals("HARD_STOP", result.decision().severity());
        assertEquals("FICO_LTV_EXCEEDS_MATRIX", result.decision().reasonCode());
        assertEquals(LOW_ROW, result.decision().matchedRowId());
        assertTrue(result.decision().message().contains("maximum LTV of 90.00%"));
    }

    @Test
    void neverInfersMissingFico() {
        FicoLtvEvaluationResult result = service(rows()).evaluate(TENANT, request(null, "0.80000"), "corr-3");

        assertEquals("INSUFFICIENT_DATA", result.decision().eligibilityStatus());
        assertEquals("MISSING_FICO", result.decision().reasonCode());
        assertNull(result.decision().matchedRowId());
    }

    @Test
    void missingMatrixFailsClosedWithoutInventedThresholds() {
        FicoLtvEvaluationResult result = service(List.of()).evaluate(TENANT, request(742, "0.80000"), "corr-4");

        assertEquals("CANNOT_DECIDE", result.decision().eligibilityStatus());
        assertEquals("MATRIX_NOT_CONFIGURED", result.decision().reasonCode());
        assertNull(result.decision().maxLtv());
    }

    private FicoLtvMatrixService service(List<FicoLtvMatrixRow> rows) {
        return new FicoLtvMatrixService(new StubRepository(rows), null, new ObjectMapper());
    }

    private FicoLtvMatrixEvaluationRequest request(Integer fico, String ltv) {
        return new FicoLtvMatrixEvaluationRequest(
            UUID.fromString("cf6e0657-e55d-4485-8e7a-968cc8758fc0"),
            1,
            "2026-05-13",
            new FicoLtvProductCandidate(UUID.fromString("11111111-1111-1111-1111-111111111111"), "CONF30", "FNMA"),
            new FicoLtvFacts(fico, new BigDecimal(ltv), new BigDecimal(ltv), "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, "PURCHASE")
        );
    }

    private List<FicoLtvMatrixRow> rows() {
        return List.of(
            row(HIGH_ROW, 740, 850, "0.97000"),
            row(UUID.fromString("55555555-5555-5555-5555-555555555556"), 720, 739, "0.95000"),
            row(UUID.fromString("55555555-5555-5555-5555-555555555557"), 700, 719, "0.90000"),
            row(LOW_ROW, 620, 699, "0.90000")
        );
    }

    private FicoLtvMatrixRow row(UUID id, int min, int max, String maxLtv) {
        return new FicoLtvMatrixRow(id, MATRIX_SET, min, max, new BigDecimal(maxLtv), new BigDecimal(maxLtv),
            "PURCHASE", "PRIMARY_RESIDENCE", "SINGLE_FAMILY", 1, 4, null, null, "WARNING", "FICO_LTV_WITHIN_MATRIX", id.toString());
    }

    private static class StubRepository extends FicoLtvMatrixRepository {
        private final List<FicoLtvMatrixRow> rows;

        StubRepository(List<FicoLtvMatrixRow> rows) {
            super(null);
            this.rows = rows;
        }

        @Override
        public FicoLtvMatrixConfig resolve(UUID tenantId, String productFamily, String investorCode, String channel,
                                           String loanPurpose, String occupancyType, String propertyType, Date effectiveDate) {
            if (rows.isEmpty()) {
                return null;
            }
            return new FicoLtvMatrixConfig(MATRIX_SET.toString(), productFamily, investorCode, channel, "PUBLISHED", 1, rows);
        }
    }
}
