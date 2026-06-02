package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.config.FicoLtvMatrixProperties;
import com.wcpe.eligibility.repository.FicoLtvMatrixRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class FicoLtvMatrixRule implements EligibilityRule {

    private final FicoLtvMatrixProperties properties;
    private final FicoLtvMatrixRepository repository;

    public FicoLtvMatrixRule(FicoLtvMatrixProperties properties, FicoLtvMatrixRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public RuleType getRuleType() {
        return RuleType.R13;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        Integer fico = request.borrowerProfile().representativeFico();
        LoanProfile loanProfile = request.loanProfile();
        PropertyProfile propertyProfile = request.propertyProfile();

        String loanPurpose = loanProfile.loanPurpose();
        String occupancyType = propertyProfile.occupancyType();
        String propertyType = propertyProfile.propertyType();
        int units = propertyProfile.units();

        BigDecimal ltv = computeLtv(loanProfile, propertyProfile);
        BigDecimal cltv = computeCltv(loanProfile, propertyProfile);

        List<FicoLtvMatrixConfig> configs = resolveMatrix(productCode, investorCode, loanPurpose);

        if (configs.isEmpty() || configs.stream().noneMatch(c -> c.rows() != null && !c.rows().isEmpty())) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R13", "FICO_LTV_MATRIX", "WARNING", "CANNOT_DECIDE",
                "MX01", "No FICO/LTV matrix configured for this product candidate.",
                null, null,
                Map.of("ruleSetVersion", 4, "deterministic", true, "phase", "matrix_resolve")
            );
        }

        if (fico == null) {
            FicoLtvMatrixRow firstConfigurable = findSeverityForMissingFico(configs, loanPurpose, occupancyType);
            String severity = firstConfigurable != null
                ? firstConfigurable.severityIfMissingFico()
                : "WARNING";
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R13", "FICO_LTV_MATRIX", severity, "INSUFFICIENT_DATA",
                "MX02", "Representative FICO is required for conventional eligibility.",
                null, null,
                Map.of("ruleSetVersion", 4, "deterministic", true, "phase", "matrix_fico_missing")
            );
        }

        if (fico < 300 || fico > 850) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R13", "FICO_LTV_MATRIX", "HARD_STOP", "INELIGIBLE",
                "MX03", "FICO score " + fico + " is outside valid range [300..850].",
                String.valueOf(fico), "300..850",
                Map.of("ruleSetVersion", 4, "deterministic", true, "phase", "matrix_fico_range")
            );
        }

        FicoLtvMatrixRow matchedRow = matchRow(configs, fico, loanPurpose, occupancyType, propertyType, units);

        if (matchedRow == null) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R13", "FICO_LTV_MATRIX", "HARD_STOP", "INELIGIBLE",
                "MX04", "FICO " + fico + " has no matching matrix band for " + productCode + "/" + investorCode + ".",
                String.valueOf(fico), null,
                Map.of("ruleSetVersion", 4, "deterministic", true, "phase", "matrix_band_match")
            );
        }

        if (ltv != null) {
            BigDecimal maxLtv = matchedRow.maxLtv();
            if (ltv.compareTo(maxLtv) > 0) {
                String ltvPct = ltv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();
                String maxLtvPct = maxLtv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();
                return new RuleDecision(
                    UUID.randomUUID(), productCode, investorCode,
                    "R13", "FICO_LTV_MATRIX", "HARD_STOP", "INELIGIBLE",
                    "MX05", "FICO " + fico + " with " + ltvPct + "% LTV is outside " + productCode + "/" + investorCode + " maximum LTV of " + maxLtvPct + "%.",
                    ltv.toPlainString(), maxLtv.toPlainString(),
                    Map.of("ruleSetVersion", 4, "deterministic", true, "matchedRowId", matchedRow.matrixRowId().toString(), "phase", "matrix_ltv_check")
                );
            }
        }

        if (cltv != null && matchedRow.maxCltv() != null) {
            BigDecimal maxCltv = matchedRow.maxCltv();
            if (cltv.compareTo(maxCltv) > 0) {
                String cltvPct = cltv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();
                String maxCltvPct = maxCltv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();
                return new RuleDecision(
                    UUID.randomUUID(), productCode, investorCode,
                    "R13", "FICO_LTV_MATRIX", "HARD_STOP", "INELIGIBLE",
                    "MX06", "CLTV " + cltvPct + "% exceeds maximum CLTV of " + maxCltvPct + "% for FICO " + fico + ".",
                    cltv.toPlainString(), maxCltv.toPlainString(),
                    Map.of("ruleSetVersion", 4, "deterministic", true, "matchedRowId", matchedRow.matrixRowId().toString(), "phase", "matrix_cltv_check")
                );
            }
        }

        BigDecimal maxLtv = matchedRow.maxLtv();
        BigDecimal effectiveMaxCltv = matchedRow.maxCltv() != null ? matchedRow.maxCltv() : maxLtv;

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("ruleSetVersion", 4);
        trace.put("deterministic", true);
        trace.put("matchedRowId", matchedRow.matrixRowId().toString());
        trace.put("phase", "matrix_pass");
        if (matchedRow.maxCltv() == null && cltv != null) {
            trace.put("ledgerReason", "CLTV_NOT_APPLICABLE");
        }

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R13", "FICO_LTV_MATRIX", "PASS", "ELIGIBLE",
            null, "FICO " + fico + " and LTV " +
                (ltv != null ? ltv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%" : "N/A") +
                " satisfy " + productCode + "/" + investorCode + " matrix.",
            String.valueOf(fico),
            maxLtv.toPlainString(),
            trace
        );
    }

    private List<FicoLtvMatrixConfig> resolveMatrix(String productCode, String investorCode, String loanPurpose) {
        String productFamily = extractProductFamily(productCode);
        if (properties.isEnabled()) {
            return properties.resolveConfigs(productFamily, investorCode, null);
        }

        List<FicoLtvMatrixConfig> dbConfigs = new ArrayList<>();
        try {
            FicoLtvMatrixConfig config = repository.resolve(
                UUID.randomUUID(), productFamily, investorCode, null,
                loanPurpose, "PRIMARY_RESIDENCE", null
            );
            if (config != null) {
                dbConfigs.add(config);
            }
        } catch (Exception e) {
            // DB not available or table empty - fall through to return empty
        }
        return dbConfigs;
    }

    private String extractProductFamily(String productCode) {
        if (productCode == null) return null;
        // CONF30 -> CONVENTIONAL, JUM15 -> JUMBO, FHA -> FHA
        if (productCode.startsWith("CONF") || productCode.startsWith("CONV")) {
            return "CONVENTIONAL";
        }
        if (productCode.startsWith("JUM")) {
            return "JUMBO";
        }
        if (productCode.startsWith("FHA")) {
            return "FHA";
        }
        return "CONVENTIONAL";
    }

    private FicoLtvMatrixRow matchRow(List<FicoLtvMatrixConfig> configs, int fico,
                                       String loanPurpose, String occupancyType,
                                       String propertyType, int units) {
        FicoLtvMatrixRow bestMatch = null;
        for (FicoLtvMatrixConfig config : configs) {
            for (FicoLtvMatrixRow row : config.rows()) {
                if (fico < row.ficoMin() || fico > row.ficoMax()) {
                    continue;
                }
                if (!matchesPurpose(row, loanPurpose)) continue;
                if (!matchesOccupancy(row, occupancyType)) continue;
                if (row.propertyType() != null && !row.propertyType().equalsIgnoreCase(propertyType)) continue;
                if (units < row.unitsMin() || units > row.unitsMax()) continue;

                if (bestMatch == null || isMoreSpecific(row, bestMatch)) {
                    bestMatch = row;
                }
            }
        }
        return bestMatch;
    }

    private boolean matchesPurpose(FicoLtvMatrixRow row, String loanPurpose) {
        return row.loanPurpose() == null || "ALL".equalsIgnoreCase(row.loanPurpose()) ||
               row.loanPurpose().equalsIgnoreCase(loanPurpose);
    }

    private boolean matchesOccupancy(FicoLtvMatrixRow row, String occupancyType) {
        return row.occupancyType() == null || "ALL".equalsIgnoreCase(row.occupancyType()) ||
               row.occupancyType().equalsIgnoreCase(occupancyType);
    }

    private boolean isMoreSpecific(FicoLtvMatrixRow candidate, FicoLtvMatrixRow current) {
        int candidateDims = countSpecificDimensions(candidate);
        int currentDims = countSpecificDimensions(current);
        return candidateDims > currentDims;
    }

    private int countSpecificDimensions(FicoLtvMatrixRow row) {
        int count = 0;
        if (row.propertyType() != null) count++;
        if (row.documentationType() != null) count++;
        if (row.ausType() != null) count++;
        if (row.loanPurpose() != null && !"ALL".equalsIgnoreCase(row.loanPurpose())) count++;
        if (row.occupancyType() != null && !"ALL".equalsIgnoreCase(row.occupancyType())) count++;
        return count;
    }

    private FicoLtvMatrixRow findSeverityForMissingFico(List<FicoLtvMatrixConfig> configs,
                                                          String loanPurpose, String occupancyType) {
        for (FicoLtvMatrixConfig config : configs) {
            for (FicoLtvMatrixRow row : config.rows()) {
                if (matchesPurpose(row, loanPurpose) && matchesOccupancy(row, occupancyType)) {
                    return row;
                }
            }
        }
        return null;
    }

    private BigDecimal computeLtv(LoanProfile loanProfile, PropertyProfile propertyProfile) {
        BigDecimal loanAmount = loanProfile.loanAmount();
        BigDecimal purchasePrice = propertyProfile.purchasePrice();
        if (loanAmount == null || purchasePrice == null) return null;
        BigDecimal collateral = purchasePrice;
        if (propertyProfile.appraisedValue() != null) {
            collateral = purchasePrice.min(propertyProfile.appraisedValue());
        }
        if (collateral.compareTo(BigDecimal.ZERO) <= 0) return null;
        return loanAmount.divide(collateral, 5, RoundingMode.HALF_UP);
    }

    private BigDecimal computeCltv(LoanProfile loanProfile, PropertyProfile propertyProfile) {
        BigDecimal loanAmount = loanProfile.loanAmount();
        BigDecimal subordinate = loanProfile.subordinateFinancingAmount();
        BigDecimal purchasePrice = propertyProfile.purchasePrice();
        if (loanAmount == null || purchasePrice == null || subordinate == null ||
            subordinate.compareTo(BigDecimal.ZERO) <= 0) return null;
        BigDecimal collateral = purchasePrice;
        if (propertyProfile.appraisedValue() != null) {
            collateral = purchasePrice.min(propertyProfile.appraisedValue());
        }
        if (collateral.compareTo(BigDecimal.ZERO) <= 0) return null;
        return loanAmount.add(subordinate).divide(collateral, 5, RoundingMode.HALF_UP);
    }
}
