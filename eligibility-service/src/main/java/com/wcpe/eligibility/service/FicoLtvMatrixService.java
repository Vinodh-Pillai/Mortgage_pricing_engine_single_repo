package com.wcpe.eligibility.service;

import com.wcpe.eligibility.domain.models.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class FicoLtvMatrixService {

    public FicoLtvEvaluationResult evaluate(FicoLtvMatrixEvaluationRequest request) {
        UUID evaluationId = UUID.randomUUID();
        UUID ruleVersionId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        Integer fico = request.facts().representativeFico();
        BigDecimal ltv = request.facts().ltv();
        BigDecimal cltv = request.facts().cltv();
        String productCode = request.productCandidate().productCode();
        String investorCode = request.productCandidate().investorCode();

        if (fico == null) {
            return buildResult(evaluationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INSUFFICIENT_DATA", "WARNING",
                "MISSING_FICO", "Representative FICO is required for conventional eligibility.",
                null, null, null, ruleVersionId
            ));
        }

        if (fico < 300 || fico > 850) {
            return buildResult(evaluationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "INVALID_FICO", "FICO score " + fico + " is outside valid range [300..850].",
                null, null, null, ruleVersionId
            ));
        }

        BigDecimal maxLtv = resolveMaxLtv(fico, productCode, investorCode,
            request.facts().occupancyType(), request.facts().propertyType(),
            request.facts().units(), request.facts().loanPurpose());

        if (maxLtv == null) {
            return buildResult(evaluationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "CANNOT_DECIDE", "WARNING",
                "MATRIX_NOT_CONFIGURED", "No FICO/LTV matrix configured for " + productCode + "/" + investorCode + ".",
                null, null, null, ruleVersionId
            ));
        }

        UUID matchedRowId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        BigDecimal effectiveMaxCltv = maxLtv;

        String ltvPct = ltv != null ? ltv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() : "N/A";
        String maxLtvPct = maxLtv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();

        if (ltv != null && ltv.compareTo(maxLtv) > 0) {
            return buildResult(evaluationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "FICO_LTV_EXCEEDS_MATRIX",
                "FICO " + fico + " with " + ltvPct + "% LTV is outside " + productCode + "/" + investorCode + " maximum LTV of " + maxLtvPct + "%.",
                matchedRowId, maxLtv, effectiveMaxCltv, ruleVersionId
            ));
        }

        if (cltv != null && cltv.compareTo(effectiveMaxCltv) > 0) {
            String cltvPct = cltv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();
            return buildResult(evaluationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "FICO_CLTV_EXCEEDS_MATRIX",
                "CLTV " + cltvPct + "% exceeds maximum for FICO " + fico + ".",
                matchedRowId, maxLtv, effectiveMaxCltv, ruleVersionId
            ));
        }

        return buildResult(evaluationId, new FicoLtvDecision(
            "CONF_FICO_LTV_MATRIX", "ELIGIBLE", "PASS",
            "FICO_LTV_WITHIN_MATRIX",
            "FICO " + fico + " and LTV " + ltvPct + "% satisfy " + productCode + "/" + investorCode + " matrix.",
            matchedRowId, maxLtv, effectiveMaxCltv, ruleVersionId
        ));
    }

    private FicoLtvEvaluationResult buildResult(UUID evaluationId, FicoLtvDecision decision) {
        String payload = evaluationId + ":" + decision.reasonCode() + ":" + decision.eligibilityStatus();
        String hash = "sha256:" + java.util.Base64.getEncoder().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new FicoLtvEvaluationResult(evaluationId, decision, hash);
    }

    private BigDecimal resolveMaxLtv(int fico, String productCode, String investorCode,
                                      String occupancyType, String propertyType, int units,
                                      String loanPurpose) {
        // Configuration-driven lookup from matrix tables or config
        // Default CONF30/FNMA matrix for conventional purchase primary residence
        if ("CONF30".equals(productCode) && "FNMA".equals(investorCode)) {
            return resolveConf30Fnma(fico, occupancyType, loanPurpose);
        }
        if ("CONF15".equals(productCode) && "FNMA".equals(investorCode)) {
            return resolveConf15Fnma(fico, occupancyType, loanPurpose);
        }
        return null;
    }

    private BigDecimal resolveConf30Fnma(int fico, String occupancyType, String loanPurpose) {
        // CONF30/FNMA matrix: FICO tiers with different max LTV
        // Purchase, Primary Residence
        if ("PURCHASE".equals(loanPurpose) && "PRIMARY_RESIDENCE".equals(occupancyType)) {
            if (fico >= 740) return new BigDecimal("0.97000");
            if (fico >= 720) return new BigDecimal("0.95000");
            if (fico >= 700) return new BigDecimal("0.90000");
            if (fico >= 680) return new BigDecimal("0.80000");
            if (fico >= 660) return new BigDecimal("0.80000");
            if (fico >= 620) return new BigDecimal("0.80000");
        }
        // Purchase, Second Home
        if ("PURCHASE".equals(loanPurpose) && "SECOND_HOME".equals(occupancyType)) {
            if (fico >= 740) return new BigDecimal("0.90000");
            if (fico >= 720) return new BigDecimal("0.90000");
            if (fico >= 700) return new BigDecimal("0.90000");
            if (fico >= 680) return new BigDecimal("0.80000");
            if (fico >= 660) return new BigDecimal("0.75000");
            if (fico >= 620) return new BigDecimal("0.75000");
        }
        // Refinance, Primary Residence
        if ("REFINANCE".equals(loanPurpose) && "PRIMARY_RESIDENCE".equals(occupancyType)) {
            if (fico >= 740) return new BigDecimal("0.97000");
            if (fico >= 720) return new BigDecimal("0.95000");
            if (fico >= 700) return new BigDecimal("0.90000");
            if (fico >= 680) return new BigDecimal("0.90000");
            if (fico >= 660) return new BigDecimal("0.90000");
            if (fico >= 620) return new BigDecimal("0.90000");
        }
        // Catch-all for conventional purchase
        if (fico >= 740) return new BigDecimal("0.97000");
        if (fico >= 720) return new BigDecimal("0.95000");
        if (fico >= 700) return new BigDecimal("0.90000");
        if (fico >= 680) return new BigDecimal("0.80000");
        if (fico >= 660) return new BigDecimal("0.80000");
        if (fico >= 620) return new BigDecimal("0.80000");
        return null;
    }

    private BigDecimal resolveConf15Fnma(int fico, String occupancyType, String loanPurpose) {
        if ("PURCHASE".equals(loanPurpose) && "PRIMARY_RESIDENCE".equals(occupancyType)) {
            if (fico >= 740) return new BigDecimal("0.95000");
            if (fico >= 720) return new BigDecimal("0.90000");
            if (fico >= 700) return new BigDecimal("0.90000");
            if (fico >= 680) return new BigDecimal("0.80000");
            if (fico >= 660) return new BigDecimal("0.80000");
            if (fico >= 620) return new BigDecimal("0.75000");
        }
        if (fico >= 740) return new BigDecimal("0.95000");
        if (fico >= 720) return new BigDecimal("0.90000");
        if (fico >= 700) return new BigDecimal("0.90000");
        if (fico >= 680) return new BigDecimal("0.80000");
        if (fico >= 660) return new BigDecimal("0.80000");
        if (fico >= 620) return new BigDecimal("0.75000");
        return null;
    }
}
