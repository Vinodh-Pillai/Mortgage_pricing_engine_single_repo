package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Configurable FICO/LTV LLPA evaluator. All bands, selectors, reason codes,
 * and deltas are supplied by a published rule-book read model.
 */
public final class FicoLtvLlpaEvaluator {
    public static final String CATEGORY = "LLPA_FICO_LTV";

    public EvaluationResult evaluate(EvaluationRequest request, List<GridCell> cells) {
        Objects.requireNonNull(request, "request is required");
        List<GridCell> configuredCells = List.copyOf(cells == null ? List.of() : cells);
        if (request.representativeFico == null) {
            return request.failClosed("representative FICO is required");
        }
        validateNoOverlaps(configuredCells);

        List<GridCell> matches = configuredCells.stream()
            .filter(GridCell::enabled)
            .filter(cell -> cell.tenantId.equals(request.tenantId))
            .filter(cell -> cell.productId.equals(request.productId))
            .filter(cell -> cell.investorId.equals(request.investorId))
            .filter(cell -> cell.channel.equals(request.channel))
            .filter(cell -> request.ruleBookVersion == null || cell.ruleBookVersion.equals(request.ruleBookVersion))
            .filter(cell -> cell.effectiveOn(request.quoteDate))
            .filter(cell -> cell.containsFico(request.representativeFico))
            .filter(cell -> cell.containsLtv(request.ltvValue(cell.ltvMetric)))
            .sorted(Comparator.comparingInt(GridCell::priority))
            .toList();

        if (matches.isEmpty()) {
            return request.failClosed("no configured FICO/LTV LLPA cell matched request");
        }
        GridCell cell = matches.get(0);
        if (matches.size() > 1 && matches.get(1).priority == cell.priority) {
            return request.conflict("multiple configured FICO/LTV LLPA cells matched with the same priority");
        }
        return request.applied(cell);
    }

    public static void validateNoOverlaps(List<GridCell> cells) {
        List<GridCell> configuredCells = List.copyOf(cells == null ? List.of() : cells);
        for (int i = 0; i < configuredCells.size(); i++) {
            GridCell left = configuredCells.get(i);
            for (int j = i + 1; j < configuredCells.size(); j++) {
                GridCell right = configuredCells.get(j);
                if (left.sameDimensionAs(right) && left.ficoOverlaps(right) && left.ltvOverlaps(right)
                    && left.effectiveWindowOverlaps(right)) {
                    throw new IllegalArgumentException("overlapping FICO/LTV grid cells for tenant/rule book/dimension");
                }
            }
        }
    }

    public enum LtvMetric {
        LTV,
        CLTV,
        HCLTV
    }

    public enum BoundaryPolicy {
        MIN_INCLUSIVE_MAX_INCLUSIVE,
        MIN_EXCLUSIVE_MAX_INCLUSIVE
    }

    public enum EvaluationStatus {
        APPLIED,
        FAIL_CLOSED,
        CONFLICT
    }

    public record EvaluationRequest(
        UUID tenantId,
        String quoteId,
        String scenarioId,
        String productId,
        String investorId,
        String channel,
        Instant quoteDate,
        BigDecimal loanAmount,
        BigDecimal priceBeforeFicoLtv,
        Integer representativeFico,
        BigDecimal ltv,
        BigDecimal cltv,
        BigDecimal hcltv,
        String ruleBookVersion
    ) {
        public EvaluationRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(quoteId, "quoteId is required");
            requireText(scenarioId, "scenarioId is required");
            requireText(productId, "productId is required");
            requireText(investorId, "investorId is required");
            requireText(channel, "channel is required");
            Objects.requireNonNull(quoteDate, "quoteDate is required");
            Objects.requireNonNull(loanAmount, "loanAmount is required");
            Objects.requireNonNull(priceBeforeFicoLtv, "priceBeforeFicoLtv is required");
        }

        BigDecimal ltvValue(LtvMetric metric) {
            return switch (metric) {
                case LTV -> ltv;
                case CLTV -> cltv;
                case HCLTV -> hcltv;
            };
        }

        EvaluationResult failClosed(String message) {
            return new EvaluationResult(
                deterministicId(tenantId + quoteId + scenarioId + message),
                CATEGORY,
                EvaluationStatus.FAIL_CLOSED,
                null,
                null,
                null,
                inputSnapshotHash(),
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                null,
                null,
                List.of(message),
                null,
                null,
                "HALF_UP"
            );
        }

        EvaluationResult conflict(String message) {
            EvaluationResult failed = failClosed(message);
            return new EvaluationResult(
                failed.adjustmentId,
                failed.category,
                EvaluationStatus.CONFLICT,
                failed.ruleId,
                failed.ruleBookId,
                failed.ruleBookVersion,
                failed.inputSnapshotHash,
                failed.pointsDelta,
                failed.bpsDelta,
                failed.moneyAmount,
                failed.reasonCode,
                failed.validationMessages,
                failed.selectedFicoBandKey,
                failed.selectedLtvBandKey,
                failed.roundingMode
            );
        }

        EvaluationResult applied(GridCell cell) {
            BigDecimal pointsDelta = cell.pointsDelta.setScale(6, RoundingMode.HALF_UP);
            BigDecimal bpsDelta = pointsDelta.multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal moneyAmount = loanAmount.multiply(pointsDelta)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            return new EvaluationResult(
                deterministicId(tenantId + quoteId + cell.ruleId + cell.contentHash),
                CATEGORY,
                EvaluationStatus.APPLIED,
                cell.ruleId,
                cell.ruleBookId,
                cell.ruleBookVersion,
                inputSnapshotHash(),
                pointsDelta,
                bpsDelta,
                moneyAmount,
                cell.reasonCode,
                List.of(),
                cell.ficoBandKey,
                cell.ltvBandKey,
                "HALF_UP"
            );
        }

        private String inputSnapshotHash() {
            return hashOf(tenantId, quoteId, scenarioId, productId, investorId, channel, quoteDate,
                loanAmount, representativeFico, ltv, cltv, hcltv, ruleBookVersion);
        }
    }

    public record GridCell(
        UUID tenantId,
        UUID ruleBookId,
        UUID ruleId,
        String ruleBookVersion,
        String ruleBookHash,
        String productId,
        String investorId,
        String channel,
        String ficoBandKey,
        int ficoMin,
        int ficoMax,
        LtvMetric ltvMetric,
        String ltvBandKey,
        BigDecimal ltvMin,
        BigDecimal ltvMax,
        BoundaryPolicy boundaryPolicy,
        BigDecimal pointsDelta,
        String reasonCode,
        int priority,
        Instant effectiveStart,
        Instant effectiveEnd,
        String contentHash,
        boolean enabled
    ) {
        public GridCell {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(ruleBookId, "ruleBookId is required");
            Objects.requireNonNull(ruleId, "ruleId is required");
            requireText(ruleBookVersion, "ruleBookVersion is required");
            requireText(ruleBookHash, "ruleBookHash is required");
            requireText(productId, "productId is required");
            requireText(investorId, "investorId is required");
            requireText(channel, "channel is required");
            requireText(ficoBandKey, "ficoBandKey is required");
            Objects.requireNonNull(ltvMetric, "ltvMetric is required");
            requireText(ltvBandKey, "ltvBandKey is required");
            Objects.requireNonNull(ltvMin, "ltvMin is required");
            Objects.requireNonNull(ltvMax, "ltvMax is required");
            boundaryPolicy = boundaryPolicy == null ? BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE : boundaryPolicy;
            Objects.requireNonNull(pointsDelta, "pointsDelta is required");
            requireText(reasonCode, "reasonCode is required");
            Objects.requireNonNull(effectiveStart, "effectiveStart is required");
            if (ficoMin > ficoMax) {
                throw new IllegalArgumentException("ficoMin cannot exceed ficoMax");
            }
            if (ltvMin.compareTo(ltvMax) > 0) {
                throw new IllegalArgumentException("ltvMin cannot exceed ltvMax");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("priority must be non-negative");
            }
            contentHash = contentHash == null || contentHash.isBlank()
                ? hashOf(tenantId, ruleBookId, ruleId, ruleBookVersion, productId, investorId, channel,
                    ficoBandKey, ficoMin, ficoMax, ltvMetric, ltvBandKey, ltvMin, ltvMax, pointsDelta, reasonCode)
                : contentHash;
        }

        boolean effectiveOn(Instant quoteDate) {
            return !quoteDate.isBefore(effectiveStart) && (effectiveEnd == null || quoteDate.isBefore(effectiveEnd));
        }

        boolean containsFico(Integer fico) {
            return fico != null && fico >= ficoMin && fico <= ficoMax;
        }

        boolean containsLtv(BigDecimal ltvValue) {
            if (ltvValue == null) {
                return false;
            }
            int minComparison = ltvValue.compareTo(ltvMin);
            int maxComparison = ltvValue.compareTo(ltvMax);
            boolean aboveMin = boundaryPolicy == BoundaryPolicy.MIN_EXCLUSIVE_MAX_INCLUSIVE
                ? minComparison > 0
                : minComparison >= 0;
            return aboveMin && maxComparison <= 0;
        }

        boolean sameDimensionAs(GridCell other) {
            return tenantId.equals(other.tenantId)
                && ruleBookId.equals(other.ruleBookId)
                && productId.equals(other.productId)
                && investorId.equals(other.investorId)
                && channel.equals(other.channel)
                && ltvMetric == other.ltvMetric;
        }

        boolean ficoOverlaps(GridCell other) {
            return ficoMin <= other.ficoMax && other.ficoMin <= ficoMax;
        }

        boolean ltvOverlaps(GridCell other) {
            return ltvMin.compareTo(other.ltvMax) <= 0 && other.ltvMin.compareTo(ltvMax) <= 0;
        }

        boolean effectiveWindowOverlaps(GridCell other) {
            Instant leftEnd = effectiveEnd == null ? Instant.MAX : effectiveEnd;
            Instant rightEnd = other.effectiveEnd == null ? Instant.MAX : other.effectiveEnd;
            return effectiveStart.isBefore(rightEnd) && other.effectiveStart.isBefore(leftEnd);
        }
    }

    public record EvaluationResult(
        UUID adjustmentId,
        String category,
        EvaluationStatus status,
        UUID ruleId,
        UUID ruleBookId,
        String ruleBookVersion,
        String inputSnapshotHash,
        BigDecimal pointsDelta,
        BigDecimal bpsDelta,
        BigDecimal moneyAmount,
        String reasonCode,
        List<String> validationMessages,
        String selectedFicoBandKey,
        String selectedLtvBandKey,
        String roundingMode
    ) {
        public EvaluationResult {
            Objects.requireNonNull(adjustmentId, "adjustmentId is required");
            requireText(category, "category is required");
            Objects.requireNonNull(status, "status is required");
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            Objects.requireNonNull(pointsDelta, "pointsDelta is required");
            Objects.requireNonNull(bpsDelta, "bpsDelta is required");
            validationMessages = List.copyOf(validationMessages == null ? List.of() : validationMessages);
            requireText(roundingMode, "roundingMode is required");
        }
    }

    public record LedgerEntry(
        UUID tenantId,
        String quoteId,
        UUID adjustmentId,
        String category,
        String ruleType,
        String inputSnapshotHash,
        BigDecimal pointsDelta,
        BigDecimal bpsDelta,
        String roundingMode,
        String ruleBookVersion,
        String contentHash,
        int waterfallSequence
    ) {
        public static LedgerEntry from(UUID tenantId, String quoteId, EvaluationResult result, int waterfallSequence) {
            return new LedgerEntry(
                tenantId,
                quoteId,
                result.adjustmentId,
                result.category,
                "FICO_LTV_LLPA",
                result.inputSnapshotHash,
                result.pointsDelta,
                result.bpsDelta,
                result.roundingMode,
                result.ruleBookVersion,
                hashOf(result.adjustmentId, result.inputSnapshotHash, result.pointsDelta, result.bpsDelta),
                waterfallSequence
            );
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static UUID deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
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
