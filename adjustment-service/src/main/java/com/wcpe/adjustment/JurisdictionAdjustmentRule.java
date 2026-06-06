package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record JurisdictionAdjustmentRule(
    UUID tenantId,
    UUID ruleBookId,
    UUID ruleId,
    String ruleBookVersion,
    StateCode stateCode,
    String countyFips,
    String municipalityCode,
    String productId,
    String investorId,
    String channel,
    String loanPurpose,
    String occupancy,
    String propertyType,
    BigDecimal minLoanAmount,
    BigDecimal maxLoanAmount,
    int specificityRank,
    String method,
    BigDecimal value,
    BigDecimal capAmount,
    BigDecimal floorAmount,
    String reasonCode,
    String sourceRef,
    int priority,
    boolean isBlocking,
    String exclusivityGroup,
    Instant effectiveStart,
    Instant effectiveEnd,
    String contentHash,
    boolean enabled
) {
    private static final Set<String> ALLOWED_METHODS = Set.of(
        "POINTS_DELTA",
        "BPS_DELTA",
        "FIXED_MONEY",
        "PERCENT_OF_LOAN_AMOUNT"
    );

    public JurisdictionAdjustmentRule {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(ruleBookId, "ruleBookId is required");
        Objects.requireNonNull(ruleId, "ruleId is required");
        ruleBookVersion = requireText(ruleBookVersion, "ruleBookVersion is required");
        stateCode = StateCode.of(Objects.requireNonNull(stateCode, "stateCode is required").code());
        countyFips = normalizeOptionalText(countyFips);
        if (countyFips != null && !countyFips.matches("\\d{5}")) {
            throw new IllegalArgumentException("countyFips must be exactly 5 digits when configured");
        }
        municipalityCode = normalizeOptionalText(municipalityCode);
        if (municipalityCode != null) {
            if (municipalityCode.length() > 32) {
                throw new IllegalArgumentException("municipalityCode must not exceed 32 characters");
            }
            if (countyFips == null) {
                throw new IllegalArgumentException("countyFips is required when municipalityCode is configured");
            }
        }
        specificityRank = municipalityCode != null ? 2 : countyFips != null ? 1 : 0;
        productId = normalizeOptionalText(productId);
        investorId = normalizeOptionalText(investorId);
        channel = normalizeOptionalText(channel);
        loanPurpose = normalizeOptionalText(loanPurpose);
        occupancy = normalizeOptionalText(occupancy);
        propertyType = normalizeOptionalText(propertyType);
        if (minLoanAmount != null && maxLoanAmount != null && minLoanAmount.compareTo(maxLoanAmount) > 0) {
            throw new IllegalArgumentException("minLoanAmount cannot exceed maxLoanAmount");
        }
        method = requireText(method, "method is required");
        if (!ALLOWED_METHODS.contains(method)) {
            throw new IllegalArgumentException("method is not supported");
        }
        Objects.requireNonNull(value, "value is required");
        if (capAmount != null && floorAmount != null && floorAmount.compareTo(capAmount) > 0) {
            throw new IllegalArgumentException("floorAmount cannot exceed capAmount");
        }
        reasonCode = requireText(reasonCode, "reasonCode is required");
        sourceRef = requireText(sourceRef, "sourceRef is required");
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be non-negative");
        }
        exclusivityGroup = normalizeOptionalText(exclusivityGroup);
        Objects.requireNonNull(effectiveStart, "effectiveStart is required");
        contentHash = contentHash == null || contentHash.isBlank()
            ? hashOf(tenantId, ruleBookId, ruleId, ruleBookVersion, stateCode.code(), countyFips, municipalityCode,
                productId, investorId, channel, loanPurpose, occupancy, propertyType, minLoanAmount, maxLoanAmount,
                specificityRank, method, value, capAmount, floorAmount, reasonCode, sourceRef, priority, isBlocking,
                exclusivityGroup, effectiveStart, effectiveEnd, enabled)
            : contentHash;
    }

    public BigDecimal applyFormula(BigDecimal loanAmount) {
        return switch (method) {
            case "POINTS_DELTA", "BPS_DELTA", "FIXED_MONEY" -> value;
            case "PERCENT_OF_LOAN_AMOUNT" -> Objects.requireNonNull(loanAmount, "loanAmount is required")
                .multiply(value)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            default -> throw new IllegalStateException("method is not supported");
        };
    }

    public BigDecimal applyCapFloor(BigDecimal rawAmount) {
        return new AdjustmentCapFloor(capAmount, floorAmount).apply(rawAmount);
    }

    public boolean effectiveOn(Instant quoteDate) {
        Objects.requireNonNull(quoteDate, "quoteDate is required");
        return !quoteDate.isBefore(effectiveStart) && (effectiveEnd == null || quoteDate.isBefore(effectiveEnd));
    }

    public static JurisdictionAdjustmentRule fromValues(
        UUID tenantId,
        UUID ruleBookId,
        UUID ruleId,
        String ruleBookVersion,
        StateCode stateCode,
        String countyFips,
        String municipalityCode,
        String method,
        BigDecimal value,
        BigDecimal capAmount,
        BigDecimal floorAmount,
        String reasonCode,
        String sourceRef,
        int priority,
        Boolean isBlocking,
        String exclusivityGroup,
        Instant effectiveStart,
        Instant effectiveEnd,
        Boolean enabled
    ) {
        return new JurisdictionAdjustmentRule(
            tenantId,
            ruleBookId,
            ruleId,
            ruleBookVersion,
            stateCode,
            countyFips,
            municipalityCode,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            method,
            value,
            capAmount,
            floorAmount,
            reasonCode,
            sourceRef,
            priority,
            isBlocking != null && isBlocking,
            exclusivityGroup,
            effectiveStart,
            effectiveEnd,
            null,
            enabled == null || enabled
        );
    }

    public static JurisdictionAdjustmentRule fromValuesWithSelectors(
        UUID tenantId,
        UUID ruleBookId,
        UUID ruleId,
        String ruleBookVersion,
        StateCode stateCode,
        String countyFips,
        String municipalityCode,
        String productId,
        String investorId,
        String channel,
        String loanPurpose,
        String occupancy,
        String propertyType,
        BigDecimal minLoanAmount,
        BigDecimal maxLoanAmount,
        String method,
        BigDecimal value,
        BigDecimal capAmount,
        BigDecimal floorAmount,
        String reasonCode,
        String sourceRef,
        int priority,
        Boolean isBlocking,
        String exclusivityGroup,
        Instant effectiveStart,
        Instant effectiveEnd,
        Boolean enabled
    ) {
        return new JurisdictionAdjustmentRule(
            tenantId,
            ruleBookId,
            ruleId,
            ruleBookVersion,
            stateCode,
            countyFips,
            municipalityCode,
            productId,
            investorId,
            channel,
            loanPurpose,
            occupancy,
            propertyType,
            minLoanAmount,
            maxLoanAmount,
            0,
            method,
            value,
            capAmount,
            floorAmount,
            reasonCode,
            sourceRef,
            priority,
            isBlocking != null && isBlocking,
            exclusivityGroup,
            effectiveStart,
            effectiveEnd,
            null,
            enabled == null || enabled
        );
    }

    private static String requireText(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String hashOf(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
