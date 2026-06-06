package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Configurable adjustment conflict detector for PII-06-S09.
 *
 * <p>The detector consumes tenant-scoped policy/configuration references and caller-supplied ledger lines.
 * It intentionally does not encode mortgage pricing thresholds, fee amounts, investor policy, or tenant rules.</p>
 */
public final class AdjustmentConflictDetector {
    public static final String EVENT_TOPIC = "pricing.quote.adjustments.v1";
    public static final String EVENT_TYPE = "QuoteAdjustmentConflictsDetected.v1";
    public static final String SOURCE_SERVICE = "adjustment-service";

    public ConflictDetectionResult detect(ConflictDetectionRequest request) {
        Objects.requireNonNull(request, "conflict detection request is required");
        request.validate();

        List<ConflictLineRef> allLines = request.allLines();
        List<ConflictFinding> findings = new ArrayList<>();
        List<String> suppressedLineIds = new ArrayList<>();
        List<AdjustedTotals> adjustedTotals = new ArrayList<>();

        for (ConflictPolicy policy : request.policySet().policies().stream()
            .filter(ConflictPolicy::enabled)
            .sorted(Comparator.comparingInt(ConflictPolicy::priority).thenComparing(ConflictPolicy::policyCode))
            .toList()) {
            switch (policy.strategy()) {
                case FAIL_ON_MULTIPLE, SUPPRESS_LOWER_PRIORITY, HIGHEST_COST, LOWEST_COST, WARN_ONLY, BLOCK_QUOTE,
                    REQUIRE_MANUAL_REVIEW -> evaluateMatchedGroups(policy, allLines, findings, suppressedLineIds);
                case CAP_TOTAL_POINTS -> evaluatePointCap(policy, allLines, request.configuration(), findings, adjustedTotals);
                case CAP_TOTAL_MONEY -> evaluateMoneyCap(policy, allLines, request.configuration(), findings, adjustedTotals);
            }
        }

        ConflictStatus status = statusFrom(findings, suppressedLineIds, adjustedTotals);
        String inputSnapshotHash = hashOf(request.tenantId(), request.quoteId(), request.scenarioId(), request.policySet().policySetId(),
            allLines, request.configuration().numericValues());
        String resultHash = hashOf(status, findings, suppressedLineIds, adjustedTotals);
        ConflictDetectionEvent event = new ConflictDetectionEvent(
            EVENT_TOPIC,
            EVENT_TYPE,
            1,
            UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.conflictRunId() + ":" + resultHash)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            SOURCE_SERVICE,
            request.occurredAt(),
            request.tenantId() + ":" + request.quoteId(),
            request.tenantId(),
            request.conflictRunId(),
            request.quoteId(),
            request.scenarioId(),
            request.policySet().policySetId(),
            request.policySet().version(),
            request.policySet().contentHash(),
            status,
            findings.stream().map(ConflictFinding::findingHash).toList(),
            List.copyOf(suppressedLineIds),
            request.correlationId(),
            request.idempotencyKey()
        );
        ConflictDetectionAudit audit = new ConflictDetectionAudit(
            request.tenantId(),
            request.conflictRunId(),
            "ADJUSTMENT_CONFLICT_DETECTION_COMPLETED",
            request.actorId(),
            request.correlationId(),
            request.policySet().contentHash(),
            inputSnapshotHash,
            resultHash
        );
        return new ConflictDetectionResult(
            request.conflictRunId(),
            status,
            request.policySet().version(),
            request.policySet().contentHash(),
            List.copyOf(findings),
            List.copyOf(suppressedLineIds),
            List.copyOf(adjustedTotals),
            inputSnapshotHash,
            event,
            audit
        );
    }

    private void evaluateMatchedGroups(
        ConflictPolicy policy,
        List<ConflictLineRef> lines,
        List<ConflictFinding> findings,
        List<String> suppressedLineIds
    ) {
        Map<String, List<ConflictLineRef>> grouped = new LinkedHashMap<>();
        for (ConflictLineRef line : policy.filterIncluded(lines)) {
            grouped.computeIfAbsent(policy.matchKey(line), ignored -> new ArrayList<>()).add(line);
        }

        for (List<ConflictLineRef> group : grouped.values()) {
            if (group.size() < 2) {
                continue;
            }
            List<ConflictLineRef> affected = group.stream()
                .sorted(Comparator.comparingInt(ConflictLineRef::priority).thenComparing(ConflictLineRef::lineId))
                .toList();
            if (policy.strategy() == ResolutionStrategy.SUPPRESS_LOWER_PRIORITY
                || policy.strategy() == ResolutionStrategy.HIGHEST_COST
                || policy.strategy() == ResolutionStrategy.LOWEST_COST) {
                ConflictLineRef selected = selectedLine(affected, policy.strategy());
                affected.stream()
                    .filter(line -> !line.lineId().equals(selected.lineId()))
                    .map(ConflictLineRef::lineId)
                    .forEach(suppressedLineIds::add);
            }
            findings.add(ConflictFinding.from(policy, affected, policy.strategy().name(), null, null));
        }
    }

    private ConflictLineRef selectedLine(List<ConflictLineRef> group, ResolutionStrategy strategy) {
        return switch (strategy) {
            case HIGHEST_COST -> group.stream()
                .max(Comparator.comparing(ConflictLineRef::totalCost).thenComparing(line -> -line.priority()))
                .orElseThrow();
            case LOWEST_COST -> group.stream()
                .min(Comparator.comparing(ConflictLineRef::totalCost).thenComparing(ConflictLineRef::priority))
                .orElseThrow();
            default -> group.stream()
                .min(Comparator.comparingInt(ConflictLineRef::priority).thenComparing(ConflictLineRef::lineId))
                .orElseThrow();
        };
    }

    private void evaluatePointCap(
        ConflictPolicy policy,
        List<ConflictLineRef> lines,
        ConflictDetectionConfiguration configuration,
        List<ConflictFinding> findings,
        List<AdjustedTotals> adjustedTotals
    ) {
        String capRef = policy.formulaParameters().get("capPointsConfigRef");
        BigDecimal cap = configuration.requireNumeric(capRef, "capPointsConfigRef").setScale(6, RoundingMode.HALF_UP);
        List<ConflictLineRef> included = policy.filterIncluded(lines);
        BigDecimal total = included.stream()
            .map(ConflictLineRef::pointsDelta)
            .reduce(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(6, RoundingMode.HALF_UP);
        if (total.compareTo(cap) > 0) {
            findings.add(ConflictFinding.from(policy, included, "CAP_TOTAL_POINTS", total, cap));
            adjustedTotals.add(new AdjustedTotals(policy.primaryCategory(), total, cap, "CAP_TOTAL_POINTS"));
        }
    }

    private void evaluateMoneyCap(
        ConflictPolicy policy,
        List<ConflictLineRef> lines,
        ConflictDetectionConfiguration configuration,
        List<ConflictFinding> findings,
        List<AdjustedTotals> adjustedTotals
    ) {
        String capRef = policy.formulaParameters().get("capMoneyConfigRef");
        BigDecimal cap = configuration.requireNumeric(capRef, "capMoneyConfigRef").setScale(2, RoundingMode.HALF_UP);
        List<ConflictLineRef> included = policy.filterIncluded(lines);
        BigDecimal total = included.stream()
            .map(ConflictLineRef::roundedAmount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(cap) > 0) {
            findings.add(ConflictFinding.from(policy, included, "CAP_TOTAL_MONEY", total, cap));
            adjustedTotals.add(new AdjustedTotals(policy.primaryCategory(), total, cap, "CAP_TOTAL_MONEY"));
        }
    }

    private ConflictStatus statusFrom(List<ConflictFinding> findings, List<String> suppressedLineIds, List<AdjustedTotals> adjustedTotals) {
        boolean blocked = findings.stream().anyMatch(finding -> finding.severity() == ConflictSeverity.BLOCKING);
        if (blocked) {
            return ConflictStatus.BLOCKED;
        }
        if (!suppressedLineIds.isEmpty() || !adjustedTotals.isEmpty()) {
            return ConflictStatus.AUTO_RESOLVED;
        }
        if (!findings.isEmpty()) {
            return ConflictStatus.WARN;
        }
        return ConflictStatus.PASS;
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

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public enum PolicySetStatus {
        DRAFT,
        VALIDATED,
        PENDING_APPROVAL,
        PUBLISHED,
        SUSPENDED,
        EXPIRED
    }

    public enum ConflictSeverity {
        INFO,
        WARNING,
        BLOCKING
    }

    public enum ConflictStatus {
        PASS,
        WARN,
        BLOCKED,
        AUTO_RESOLVED
    }

    public enum ResolutionStrategy {
        FAIL_ON_MULTIPLE,
        SUPPRESS_LOWER_PRIORITY,
        HIGHEST_COST,
        LOWEST_COST,
        CAP_TOTAL_POINTS,
        CAP_TOTAL_MONEY,
        WARN_ONLY,
        BLOCK_QUOTE,
        REQUIRE_MANUAL_REVIEW
    }

    public enum MatchCriterion {
        CATEGORY,
        REASON_CODE,
        EXCLUSIVITY_GROUP,
        FEE_CODE,
        JURISDICTION_KEY,
        COMPENSATION_PLAN_REF
    }

    public record EffectiveWindow(Instant start, Instant end) {
        public EffectiveWindow {
            Objects.requireNonNull(start, "effective start is required");
            if (end != null && !end.isAfter(start)) {
                throw new IllegalArgumentException("effective end must be after start");
            }
        }

        boolean contains(Instant instant) {
            Objects.requireNonNull(instant, "effective instant is required");
            return !instant.isBefore(start) && (end == null || instant.isBefore(end));
        }
    }

    public record ConflictPolicySet(
        UUID tenantId,
        UUID policySetId,
        int version,
        PolicySetStatus status,
        EffectiveWindow effectiveWindow,
        List<ConflictPolicy> policies,
        String createdBy,
        String approvedBy,
        Instant publishedAt,
        String contentHash
    ) {
        public ConflictPolicySet {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(policySetId, "policySetId is required");
            if (version < 1) {
                throw new IllegalArgumentException("policy set version must be positive");
            }
            Objects.requireNonNull(status, "policy set status is required");
            Objects.requireNonNull(effectiveWindow, "effective window is required");
            policies = List.copyOf(policies == null ? List.of() : policies);
            requireText(createdBy, "createdBy is required");
            if (status == PolicySetStatus.PUBLISHED) {
                requireText(approvedBy, "published conflict policy set requires approvedBy");
                Objects.requireNonNull(publishedAt, "published conflict policy set requires publishedAt");
            }
            contentHash = contentHash == null || contentHash.isBlank()
                ? hashOf(tenantId, policySetId, version, status, effectiveWindow, policies)
                : contentHash;
        }
    }

    public record ConflictPolicy(
        UUID policyId,
        String policyCode,
        ConflictSeverity severity,
        Set<String> categoriesIncluded,
        List<MatchCriterion> matchCriteria,
        ResolutionStrategy strategy,
        Map<String, String> formulaParameters,
        String remediationMessage,
        String reasonCode,
        int priority,
        boolean enabled,
        String sourceRef
    ) {
        public ConflictPolicy {
            Objects.requireNonNull(policyId, "policyId is required");
            policyCode = requireText(policyCode, "policyCode is required");
            Objects.requireNonNull(severity, "severity is required");
            categoriesIncluded = Set.copyOf(categoriesIncluded == null ? Set.of() : categoriesIncluded);
            matchCriteria = List.copyOf(matchCriteria == null ? List.of() : matchCriteria);
            Objects.requireNonNull(strategy, "resolution strategy is required");
            formulaParameters = Map.copyOf(formulaParameters == null ? Map.of() : formulaParameters);
            remediationMessage = requireText(remediationMessage, "remediationMessage is required");
            reasonCode = requireText(reasonCode, "reasonCode is required");
            if (priority < 0) {
                throw new IllegalArgumentException("policy priority must be non-negative");
            }
            sourceRef = requireText(sourceRef, "sourceRef is required");
        }

        List<ConflictLineRef> filterIncluded(List<ConflictLineRef> lines) {
            return lines.stream()
                .filter(line -> categoriesIncluded.isEmpty() || categoriesIncluded.contains(line.category()))
                .toList();
        }

        String matchKey(ConflictLineRef line) {
            if (matchCriteria.isEmpty()) {
                return line.category() + "|" + line.reasonCode() + "|" + line.exclusivityGroup();
            }
            List<String> parts = new ArrayList<>();
            for (MatchCriterion criterion : matchCriteria) {
                parts.add(switch (criterion) {
                    case CATEGORY -> line.category();
                    case REASON_CODE -> line.reasonCode();
                    case EXCLUSIVITY_GROUP -> line.exclusivityGroup();
                    case FEE_CODE -> line.feeCode();
                    case JURISDICTION_KEY -> line.jurisdictionKey();
                    case COMPENSATION_PLAN_REF -> line.compensationPlanRef();
                });
            }
            return String.join("|", parts);
        }

        String primaryCategory() {
            return categoriesIncluded.stream().sorted().findFirst().orElse("ALL");
        }
    }

    public record ConflictLineRef(
        String lineId,
        String category,
        String reasonCode,
        String exclusivityGroup,
        String feeCode,
        String jurisdictionKey,
        String compensationPlanRef,
        BigDecimal pointsDelta,
        BigDecimal roundedAmount,
        int priority,
        String sourceRef
    ) {
        public ConflictLineRef {
            lineId = requireText(lineId, "lineId is required");
            category = requireText(category, "category is required");
            reasonCode = requireText(reasonCode, "reasonCode is required");
            exclusivityGroup = normalizeOptional(exclusivityGroup);
            feeCode = normalizeOptional(feeCode);
            jurisdictionKey = normalizeOptional(jurisdictionKey);
            compensationPlanRef = normalizeOptional(compensationPlanRef);
            pointsDelta = (pointsDelta == null ? BigDecimal.ZERO : pointsDelta).setScale(6, RoundingMode.HALF_UP);
            roundedAmount = (roundedAmount == null ? BigDecimal.ZERO : roundedAmount).setScale(2, RoundingMode.HALF_UP);
            if (priority < 0) {
                throw new IllegalArgumentException("line priority must be non-negative");
            }
            sourceRef = requireText(sourceRef, "sourceRef is required");
        }

        BigDecimal totalCost() {
            return pointsDelta.abs().add(roundedAmount.abs());
        }
    }

    public record ConflictDetectionConfiguration(Map<String, BigDecimal> numericValues) {
        public ConflictDetectionConfiguration {
            numericValues = Map.copyOf(numericValues == null ? Map.of() : numericValues);
        }

        public static ConflictDetectionConfiguration empty() {
            return new ConflictDetectionConfiguration(Map.of());
        }

        BigDecimal requireNumeric(String configRef, String parameterName) {
            String normalizedRef = requireText(configRef, parameterName + " is required");
            BigDecimal value = numericValues.get(normalizedRef);
            if (value == null) {
                throw new IllegalArgumentException("missing configured numeric value for " + normalizedRef);
            }
            return value;
        }
    }

    public record ConflictDetectionRequest(
        UUID tenantId,
        UUID conflictRunId,
        String quoteId,
        String scenarioId,
        Instant quoteDate,
        List<ConflictLineRef> adjustmentLedgerLines,
        List<ConflictLineRef> feeLines,
        List<ConflictLineRef> compensationHookLines,
        ConflictPolicySet policySet,
        ConflictDetectionConfiguration configuration,
        String actorId,
        String correlationId,
        String idempotencyKey,
        Instant occurredAt
    ) {
        public ConflictDetectionRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(conflictRunId, "conflictRunId is required");
            quoteId = requireText(quoteId, "quoteId is required");
            scenarioId = requireText(scenarioId, "scenarioId is required");
            Objects.requireNonNull(quoteDate, "quoteDate is required");
            adjustmentLedgerLines = List.copyOf(adjustmentLedgerLines == null ? List.of() : adjustmentLedgerLines);
            feeLines = List.copyOf(feeLines == null ? List.of() : feeLines);
            compensationHookLines = List.copyOf(compensationHookLines == null ? List.of() : compensationHookLines);
            Objects.requireNonNull(policySet, "policySet is required");
            configuration = configuration == null ? ConflictDetectionConfiguration.empty() : configuration;
            actorId = requireText(actorId, "actorId is required");
            correlationId = requireText(correlationId, "correlationId is required");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
        }

        void validate() {
            if (!policySet.tenantId().equals(tenantId)) {
                throw new IllegalArgumentException("policy set tenant must match request tenant");
            }
            if (policySet.status() != PolicySetStatus.PUBLISHED || !policySet.effectiveWindow().contains(quoteDate)) {
                throw new IllegalStateException("conflict detection requires a published conflict policy set effective for quote date");
            }
        }

        List<ConflictLineRef> allLines() {
            List<ConflictLineRef> lines = new ArrayList<>();
            lines.addAll(adjustmentLedgerLines);
            lines.addAll(feeLines);
            lines.addAll(compensationHookLines);
            return List.copyOf(lines);
        }
    }

    public record ConflictFinding(
        UUID findingId,
        String policyCode,
        ConflictSeverity severity,
        List<String> affectedLineIds,
        String resolutionAction,
        String remediationMessage,
        String reasonCode,
        BigDecimal computedValue,
        BigDecimal configuredLimit,
        String policyHash,
        String findingHash
    ) {
        public ConflictFinding {
            Objects.requireNonNull(findingId, "findingId is required");
            policyCode = requireText(policyCode, "policyCode is required");
            Objects.requireNonNull(severity, "severity is required");
            affectedLineIds = List.copyOf(affectedLineIds == null ? List.of() : affectedLineIds);
            resolutionAction = requireText(resolutionAction, "resolutionAction is required");
            remediationMessage = requireText(remediationMessage, "remediationMessage is required");
            reasonCode = requireText(reasonCode, "reasonCode is required");
            policyHash = requireText(policyHash, "policyHash is required");
            findingHash = findingHash == null || findingHash.isBlank()
                ? hashOf(policyCode, severity, affectedLineIds, resolutionAction, computedValue, configuredLimit, policyHash)
                : findingHash;
        }

        static ConflictFinding from(
            ConflictPolicy policy,
            List<ConflictLineRef> affectedLines,
            String resolutionAction,
            BigDecimal computedValue,
            BigDecimal configuredLimit
        ) {
            List<String> lineIds = affectedLines.stream().map(ConflictLineRef::lineId).sorted().toList();
            String policyHash = hashOf(policy.policyId(), policy.policyCode(), policy.strategy(), policy.formulaParameters(), policy.sourceRef());
            return new ConflictFinding(
                UUID.nameUUIDFromBytes((policy.policyCode() + ":" + lineIds + ":" + resolutionAction)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                policy.policyCode(),
                policy.strategy() == ResolutionStrategy.BLOCK_QUOTE ? ConflictSeverity.BLOCKING : policy.severity(),
                lineIds,
                resolutionAction,
                policy.remediationMessage(),
                policy.reasonCode(),
                computedValue,
                configuredLimit,
                policyHash,
                null
            );
        }
    }

    public record AdjustedTotals(String category, BigDecimal originalTotal, BigDecimal adjustedTotal, String reason) {
        public AdjustedTotals {
            category = requireText(category, "category is required");
            Objects.requireNonNull(originalTotal, "originalTotal is required");
            Objects.requireNonNull(adjustedTotal, "adjustedTotal is required");
            reason = requireText(reason, "reason is required");
        }
    }

    public record ConflictDetectionResult(
        UUID conflictRunId,
        ConflictStatus status,
        int policyVersion,
        String policyHash,
        List<ConflictFinding> conflicts,
        List<String> suppressedLineIds,
        List<AdjustedTotals> adjustedTotals,
        String inputSnapshotHash,
        ConflictDetectionEvent event,
        ConflictDetectionAudit audit
    ) {
        public ConflictDetectionResult {
            Objects.requireNonNull(conflictRunId, "conflictRunId is required");
            Objects.requireNonNull(status, "status is required");
            policyHash = requireText(policyHash, "policyHash is required");
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
            suppressedLineIds = List.copyOf(suppressedLineIds == null ? List.of() : suppressedLineIds);
            adjustedTotals = List.copyOf(adjustedTotals == null ? List.of() : adjustedTotals);
            inputSnapshotHash = requireText(inputSnapshotHash, "inputSnapshotHash is required");
            Objects.requireNonNull(event, "event is required");
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record ConflictDetectionEvent(
        String topic,
        String eventType,
        int eventVersion,
        UUID eventId,
        String sourceService,
        Instant occurredAt,
        String eventKey,
        UUID tenantId,
        UUID conflictRunId,
        String quoteId,
        String scenarioId,
        UUID policySetId,
        int policySetVersion,
        String policySetHash,
        ConflictStatus status,
        List<String> findingHashes,
        List<String> suppressedLineIds,
        String correlationId,
        String idempotencyKey
    ) {}

    public record ConflictDetectionAudit(
        UUID tenantId,
        UUID conflictRunId,
        String action,
        String actorId,
        String correlationId,
        String policyHash,
        String inputSnapshotHash,
        String resultHash
    ) {}

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
