package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Read-only adjustment summary projector for PII-06-S10.
 *
 * <p>The projector verifies caller-supplied ledger values and does not recalculate LLPA,
 * fee, compensation, or conflict business rules.</p>
 */
public final class AdjustmentSummaryProjector {
    public static final String EVENT_TYPE = "QuoteAdjustmentSummaryGenerated.v1";
    public static final String SOURCE_SERVICE = "adjustment-service";

    public AdjustmentSummary project(AdjustmentSummaryRequest request) {
        Objects.requireNonNull(request, "adjustment summary request is required");
        request.validate();

        List<AdjustmentSummaryLine> orderedLines = request.lines().stream()
            .sorted(Comparator.comparingInt(AdjustmentSummaryLine::sequence).thenComparing(AdjustmentSummaryLine::ledgerLineId))
            .toList();
        SummaryTotals totals = SummaryTotals.from(orderedLines, request.basePrice(), request.finalAdjustedPrice());
        SummaryStatus status = statusFrom(request.conflicts(), request.staleConfigWarnings(), request.replayReferences(), totals);
        String ledgerHash = hashOf(orderedLines);
        String totalsHash = hashOf(totals, request.inputSnapshotHash(), ledgerHash);
        UUID summaryId = request.summaryId() == null
            ? UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.quoteId() + ":" + request.pricingRunId() + ":" + ledgerHash)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            : request.summaryId();

        List<AdjustmentCategorySummary> categories = orderedLines.stream()
            .map(AdjustmentSummaryLine::category)
            .distinct()
            .map(category -> AdjustmentCategorySummary.from(category, orderedLines))
            .toList();
        AdjustmentSummaryEvent event = new AdjustmentSummaryEvent(
            EVENT_TYPE,
            1,
            UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.quoteId() + ":" + totalsHash)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            SOURCE_SERVICE,
            request.generatedAt(),
            request.tenantId() + ":" + request.quoteId(),
            request.tenantId(),
            summaryId,
            request.quoteId(),
            request.scenarioId(),
            request.pricingRunId(),
            status,
            totalsHash,
            ledgerHash,
            request.correlationId()
        );
        AdjustmentSummaryAudit audit = new AdjustmentSummaryAudit(
            request.tenantId(),
            summaryId,
            "ADJUSTMENT_SUMMARY_VIEW_COMPLETED",
            request.actorId(),
            request.correlationId(),
            request.inputSnapshotHash(),
            ledgerHash,
            totalsHash
        );

        return new AdjustmentSummary(
            summaryId,
            request.tenantId(),
            request.quoteId(),
            request.scenarioId(),
            request.pricingRunId(),
            status,
            request.generatedAt(),
            request.inputSnapshotHash(),
            ledgerHash,
            categories,
            totals,
            request.conflicts(),
            request.staleConfigWarnings(),
            request.replayReferences(),
            event,
            audit
        );
    }

    private static SummaryStatus statusFrom(
        List<SummaryConflict> conflicts,
        List<String> staleConfigWarnings,
        List<ReplayReference> replayReferences,
        SummaryTotals totals
    ) {
        if (conflicts.stream().anyMatch(conflict -> conflict.severity() == ConflictSeverity.BLOCKING)) {
            return SummaryStatus.BLOCKED_CONFLICTS;
        }
        if (!staleConfigWarnings.isEmpty()) {
            return SummaryStatus.STALE_CONFIGURATION;
        }
        if (replayReferences.stream().anyMatch(reference -> !reference.replayHashMatches())) {
            return SummaryStatus.REPLAY_MISMATCH;
        }
        if (!totals.finalPriceReconciles()) {
            return SummaryStatus.WARNINGS;
        }
        return SummaryStatus.READY;
    }

    static String hashOf(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static BigDecimal normalizePoints(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeBps(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public enum SummaryStatus {
        READY,
        WARNINGS,
        BLOCKED_CONFLICTS,
        STALE_CONFIGURATION,
        REPLAY_MISMATCH
    }

    public enum LineStatus {
        APPLIED,
        SUPPRESSED,
        BLOCKED,
        WARNING
    }

    public enum ConflictSeverity {
        INFO,
        WARNING,
        BLOCKING
    }

    public record AdjustmentSummaryRequest(
        UUID tenantId,
        UUID summaryId,
        String quoteId,
        String scenarioId,
        String pricingRunId,
        String actorId,
        BigDecimal basePrice,
        BigDecimal finalAdjustedPrice,
        String inputSnapshotHash,
        List<AdjustmentSummaryLine> lines,
        List<SummaryConflict> conflicts,
        List<String> staleConfigWarnings,
        List<ReplayReference> replayReferences,
        Instant generatedAt,
        String correlationId
    ) {
        public AdjustmentSummaryRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            quoteId = requireText(quoteId, "quoteId is required");
            scenarioId = requireText(scenarioId, "scenarioId is required");
            pricingRunId = requireText(pricingRunId, "pricingRunId is required");
            actorId = requireText(actorId, "actorId is required");
            basePrice = normalizePoints(basePrice);
            finalAdjustedPrice = normalizePoints(finalAdjustedPrice);
            inputSnapshotHash = requireText(inputSnapshotHash, "inputSnapshotHash is required");
            lines = List.copyOf(lines == null ? List.of() : lines);
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
            staleConfigWarnings = List.copyOf(staleConfigWarnings == null ? List.of() : staleConfigWarnings);
            replayReferences = List.copyOf(replayReferences == null ? List.of() : replayReferences);
            generatedAt = generatedAt == null ? Instant.now() : generatedAt;
            correlationId = requireText(correlationId, "correlationId is required");
        }

        void validate() {
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("at least one summary line is required");
            }
            for (AdjustmentSummaryLine line : lines) {
                if (line.sequence() <= 0) {
                    throw new IllegalArgumentException("summary line sequence must be positive");
                }
            }
        }
    }

    public record AdjustmentSummaryLine(
        String ledgerLineId,
        int sequence,
        String category,
        String label,
        RedactedInputSummary sourceInputsRedacted,
        FormulaDisplay formulaDisplay,
        BigDecimal rawValue,
        BigDecimal roundedValue,
        BigDecimal pointsDelta,
        BigDecimal bpsDelta,
        BigDecimal moneyAmount,
        LineStatus status,
        String reasonCode,
        String ruleId,
        String configVersion,
        String contentHash,
        Instant appliedAt,
        String suppressionReason,
        String sourceRef
    ) {
        public AdjustmentSummaryLine {
            ledgerLineId = requireText(ledgerLineId, "ledgerLineId is required");
            category = requireText(category, "category is required").toUpperCase(java.util.Locale.ROOT);
            label = requireText(label, "label is required");
            sourceInputsRedacted = sourceInputsRedacted == null ? RedactedInputSummary.empty() : sourceInputsRedacted;
            formulaDisplay = formulaDisplay == null ? FormulaDisplay.persistedValue() : formulaDisplay;
            rawValue = rawValue == null ? null : rawValue.setScale(6, RoundingMode.HALF_UP);
            roundedValue = roundedValue == null ? null : roundedValue.setScale(6, RoundingMode.HALF_UP);
            pointsDelta = normalizePoints(pointsDelta);
            bpsDelta = normalizeBps(bpsDelta);
            moneyAmount = normalizeMoney(moneyAmount);
            Objects.requireNonNull(status, "line status is required");
            reasonCode = requireText(reasonCode, "reasonCode is required");
            ruleId = requireText(ruleId, "ruleId is required");
            configVersion = requireText(configVersion, "configVersion is required");
            contentHash = contentHash == null || contentHash.isBlank()
                ? hashOf(ledgerLineId, sequence, category, label, pointsDelta, bpsDelta, moneyAmount, status, reasonCode, ruleId, configVersion)
                : contentHash;
            appliedAt = appliedAt == null ? Instant.EPOCH : appliedAt;
            suppressionReason = suppressionReason == null ? "" : suppressionReason.trim();
            sourceRef = requireText(sourceRef, "sourceRef is required");
        }

        boolean includedInTotals() {
            return status == LineStatus.APPLIED;
        }
    }

    public record RedactedInputSummary(Map<String, String> values) {
        private static final List<String> SENSITIVE_KEYS = List.of("fico", "cashout", "cash_out", "personnel", "employee", "borrower", "ssn", "tin");

        public RedactedInputSummary {
            values = Map.copyOf(new TreeMap<>(values == null ? Map.of() : values));
        }

        public static RedactedInputSummary empty() {
            return new RedactedInputSummary(Map.of());
        }

        public static RedactedInputSummary of(Map<String, String> rawValues) {
            Map<String, String> redacted = new TreeMap<>();
            for (Map.Entry<String, String> entry : (rawValues == null ? Map.<String, String>of() : rawValues).entrySet()) {
                String key = requireText(entry.getKey(), "source input key is required");
                String lower = key.toLowerCase(java.util.Locale.ROOT);
                boolean sensitive = SENSITIVE_KEYS.stream().anyMatch(lower::contains);
                redacted.put(key, sensitive ? "REDACTED" : String.valueOf(entry.getValue()));
            }
            return new RedactedInputSummary(redacted);
        }
    }

    public record FormulaDisplay(String text, String source) {
        public FormulaDisplay {
            text = requireText(text, "formula display is required");
            source = requireText(source, "formula source is required");
        }

        public static FormulaDisplay persistedValue() {
            return new FormulaDisplay("Persisted ledger value", "PERSISTED_LEDGER");
        }
    }

    public record AdjustmentCategorySummary(
        String categoryCode,
        String displayName,
        BigDecimal subtotalPoints,
        BigDecimal subtotalBps,
        BigDecimal subtotalMoney,
        List<AdjustmentSummaryLine> lines
    ) {
        public AdjustmentCategorySummary {
            categoryCode = requireText(categoryCode, "categoryCode is required");
            displayName = requireText(displayName, "displayName is required");
            subtotalPoints = normalizePoints(subtotalPoints);
            subtotalBps = normalizeBps(subtotalBps);
            subtotalMoney = normalizeMoney(subtotalMoney);
            lines = List.copyOf(lines == null ? List.of() : lines);
        }

        static AdjustmentCategorySummary from(String category, List<AdjustmentSummaryLine> allLines) {
            List<AdjustmentSummaryLine> categoryLines = allLines.stream()
                .filter(line -> category.equals(line.category()))
                .toList();
            return new AdjustmentCategorySummary(
                category,
                category.replace('_', ' '),
                sumPoints(categoryLines),
                sumBps(categoryLines),
                sumMoney(categoryLines),
                categoryLines
            );
        }
    }

    public record SummaryTotals(
        BigDecimal basePrice,
        BigDecimal totalPoints,
        BigDecimal totalBps,
        BigDecimal borrowerTotal,
        BigDecimal lenderTotal,
        BigDecimal thirdPartyTotal,
        BigDecimal finalAdjustedPrice,
        boolean finalPriceReconciles
    ) {
        public SummaryTotals {
            basePrice = normalizePoints(basePrice);
            totalPoints = normalizePoints(totalPoints);
            totalBps = normalizeBps(totalBps);
            borrowerTotal = normalizeMoney(borrowerTotal);
            lenderTotal = normalizeMoney(lenderTotal);
            thirdPartyTotal = normalizeMoney(thirdPartyTotal);
            finalAdjustedPrice = normalizePoints(finalAdjustedPrice);
        }

        static SummaryTotals from(List<AdjustmentSummaryLine> lines, BigDecimal basePrice, BigDecimal finalAdjustedPrice) {
            BigDecimal points = sumPoints(lines);
            BigDecimal finalPrice = normalizePoints(finalAdjustedPrice);
            BigDecimal base = normalizePoints(basePrice);
            return new SummaryTotals(
                base,
                points,
                normalizeBps(points.movePointRight(2)),
                sumMoneyByCategory(lines, "BORROWER"),
                sumMoneyByCategory(lines, "LENDER"),
                sumMoneyByCategory(lines, "THIRD_PARTY"),
                finalPrice,
                base.add(points).setScale(6, RoundingMode.HALF_UP).compareTo(finalPrice) == 0
            );
        }
    }

    public record ReplayReference(String sourceRef, String configHash, String replayHash, boolean replayHashMatches) {
        public ReplayReference {
            sourceRef = requireText(sourceRef, "sourceRef is required");
            configHash = requireText(configHash, "configHash is required");
            replayHash = requireText(replayHash, "replayHash is required");
        }
    }

    public record SummaryConflict(String conflictId, ConflictSeverity severity, String reasonCode, String message) {
        public SummaryConflict {
            conflictId = requireText(conflictId, "conflictId is required");
            Objects.requireNonNull(severity, "conflict severity is required");
            reasonCode = requireText(reasonCode, "reasonCode is required");
            message = requireText(message, "message is required");
        }
    }

    public record AdjustmentSummary(
        UUID summaryId,
        UUID tenantId,
        String quoteId,
        String scenarioId,
        String pricingRunId,
        SummaryStatus status,
        Instant generatedAt,
        String inputSnapshotHash,
        String ledgerHash,
        List<AdjustmentCategorySummary> categories,
        SummaryTotals totals,
        List<SummaryConflict> conflicts,
        List<String> staleConfigWarnings,
        List<ReplayReference> auditRefs,
        AdjustmentSummaryEvent event,
        AdjustmentSummaryAudit audit
    ) {
        public AdjustmentSummary {
            Objects.requireNonNull(summaryId, "summaryId is required");
            Objects.requireNonNull(tenantId, "tenantId is required");
            quoteId = requireText(quoteId, "quoteId is required");
            scenarioId = requireText(scenarioId, "scenarioId is required");
            pricingRunId = requireText(pricingRunId, "pricingRunId is required");
            Objects.requireNonNull(status, "status is required");
            Objects.requireNonNull(generatedAt, "generatedAt is required");
            inputSnapshotHash = requireText(inputSnapshotHash, "inputSnapshotHash is required");
            ledgerHash = requireText(ledgerHash, "ledgerHash is required");
            categories = List.copyOf(categories == null ? List.of() : categories);
            Objects.requireNonNull(totals, "totals are required");
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
            staleConfigWarnings = List.copyOf(staleConfigWarnings == null ? List.of() : staleConfigWarnings);
            auditRefs = List.copyOf(auditRefs == null ? List.of() : auditRefs);
            Objects.requireNonNull(event, "event is required");
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record AdjustmentSummaryEvent(
        String eventType,
        int eventVersion,
        UUID eventId,
        String sourceService,
        Instant occurredAt,
        String eventKey,
        UUID tenantId,
        UUID summaryId,
        String quoteId,
        String scenarioId,
        String pricingRunId,
        SummaryStatus status,
        String totalsHash,
        String ledgerHash,
        String correlationId
    ) {}

    public record AdjustmentSummaryAudit(
        UUID tenantId,
        UUID summaryId,
        String action,
        String actorId,
        String correlationId,
        String inputSnapshotHash,
        String ledgerHash,
        String replayHash
    ) {}

    private static BigDecimal sumPoints(List<AdjustmentSummaryLine> lines) {
        return lines.stream()
            .filter(AdjustmentSummaryLine::includedInTotals)
            .map(AdjustmentSummaryLine::pointsDelta)
            .reduce(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumBps(List<AdjustmentSummaryLine> lines) {
        return lines.stream()
            .filter(AdjustmentSummaryLine::includedInTotals)
            .map(AdjustmentSummaryLine::bpsDelta)
            .reduce(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumMoney(List<AdjustmentSummaryLine> lines) {
        return lines.stream()
            .filter(AdjustmentSummaryLine::includedInTotals)
            .map(AdjustmentSummaryLine::moneyAmount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumMoneyByCategory(List<AdjustmentSummaryLine> lines, String category) {
        List<AdjustmentSummaryLine> matched = new ArrayList<>();
        for (AdjustmentSummaryLine line : lines) {
            if (line.includedInTotals() && category.equals(line.category())) {
                matched.add(line);
            }
        }
        return sumMoney(matched);
    }
}
