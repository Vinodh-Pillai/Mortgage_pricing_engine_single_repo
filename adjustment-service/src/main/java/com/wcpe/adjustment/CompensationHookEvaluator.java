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
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tenant-configured compensation hook evaluator for PII-06-S08.
 *
 * <p>This slice deliberately consumes approved compensation plan snapshots and
 * hook mappings supplied by configuration/read models. It does not own
 * compensation plans and does not encode mortgage pricing policy, rates,
 * thresholds, channels, investors, or compensation amounts in code.</p>
 */
public final class CompensationHookEvaluator {
    public static final String EVENT_TYPE = "QuoteAdjustmentApplied.v1";
    public static final String EVENT_CATEGORY = "COMPENSATION_HOOK";
    public static final String AUDIT_ACTION = "COMPENSATION_HOOK_EVALUATED";
    public static final String SOURCE_SERVICE = "adjustment-service";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public CompensationHookEvaluation evaluate(CompensationHookRequest request) {
        Objects.requireNonNull(request, "compensation hook request is required");
        request.validate();

        List<CompensationHookResult> results = new ArrayList<>();
        List<CompensationHookRule> eligibleRules = request.rules().stream()
            .filter(rule -> rule.matches(request))
            .sorted(Comparator.comparingInt(CompensationHookRule::priority).thenComparing(CompensationHookRule::mappingId))
            .toList();

        String inputSnapshotHash = hashOf(
            request.tenantId(),
            request.quoteId(),
            request.scenarioId(),
            request.loanOfficerToken(),
            request.branchToken(),
            request.channel(),
            request.product(),
            request.investor(),
            request.loanAmount(),
            request.priceBeforeHooks(),
            request.planSnapshot(),
            eligibleRules
        );

        int sequence = request.startingWaterfallSequence();
        for (CompensationHookRule rule : eligibleRules) {
            HookComputation computation = compute(rule, request);
            BigDecimal pointsDelta = computation.pointsDelta() == null ? null : normalizePoints(computation.pointsDelta());
            BigDecimal moneyAmount = computation.moneyAmount() == null ? null : normalizeMoney(computation.moneyAmount());
            results.add(new CompensationHookResult(
                UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.quoteId() + ":" + rule.mappingId() + ":" + inputSnapshotHash)
                    .getBytes(StandardCharsets.UTF_8)),
                rule.mappingId(),
                rule.hookType(),
                computation.status(),
                pointsDelta,
                moneyAmount,
                computation.formulaInputs(),
                rule.visibilityPolicy().labelForResult(),
                rule.reasonCode(),
                sequence++,
                inputSnapshotHash
            ));
        }

        String resultHash = hashOf(results);
        CompensationHookEvent event = new CompensationHookEvent(
            EVENT_TYPE,
            1,
            UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.quoteId() + ":" + resultHash).getBytes(StandardCharsets.UTF_8)),
            SOURCE_SERVICE,
            request.tenantId() + ":" + request.quoteId(),
            request.tenantId(),
            request.quoteId(),
            request.scenarioId(),
            request.planSnapshot().planRef(),
            results.stream().map(CompensationHookResult::mappingId).toList(),
            resultHash,
            request.correlationId(),
            request.idempotencyKey(),
            request.occurredAt()
        );
        CompensationHookAudit audit = new CompensationHookAudit(
            request.tenantId(),
            AUDIT_ACTION,
            request.loanOfficerToken().value(),
            request.correlationId(),
            request.planSnapshot().snapshotHash(),
            inputSnapshotHash,
            resultHash
        );
        return new CompensationHookEvaluation(results, inputSnapshotHash, event, audit);
    }

    private HookComputation compute(CompensationHookRule rule, CompensationHookRequest request) {
        return switch (rule.hookType()) {
            case PRICE_POINTS_DELTA -> {
                BigDecimal points = requireAmount(rule, "pointsDelta");
                yield amountResult(rule, points, null, Map.of("pointsDelta", points.toPlainString()));
            }
            case BPS_DELTA -> {
                BigDecimal bps = requireAmount(rule, "bps");
                BigDecimal points = bps.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
                yield amountResult(rule, points, null, Map.of("bps", bps.toPlainString()));
            }
            case FIXED_MONEY_FEE -> {
                BigDecimal amount = requireAmount(rule, "moneyAmount");
                yield amountResult(rule, null, amount, Map.of("moneyAmount", amount.toPlainString()));
            }
            case PERCENT_OF_LOAN_AMOUNT -> {
                BigDecimal percent = requireAmount(rule, "percent");
                BigDecimal amount = request.loanAmount().multiply(percent).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
                yield amountResult(rule, null, amount, Map.of(
                    "percent", percent.toPlainString(),
                    "loanAmount", request.loanAmount().toPlainString()
                ));
            }
            case FEE_CREDIT -> {
                BigDecimal amount = requireAmount(rule, "moneyAmount");
                if (amount.signum() > 0) {
                    throw new IllegalArgumentException("FEE_CREDIT requires a zero or negative configured moneyAmount");
                }
                yield amountResult(rule, null, amount, Map.of("moneyAmount", amount.toPlainString()));
            }
            case LABEL_ONLY -> new HookComputation(HookStatus.APPLIED, null, null, Map.of("labelOnly", "true"));
            case BLOCKING_POLICY -> new HookComputation(HookStatus.BLOCKED, null, null, Map.of("blockingPolicy", rule.reasonCode()));
        };
    }

    private HookComputation amountResult(CompensationHookRule rule, BigDecimal points, BigDecimal money, Map<String, String> formulaInputs) {
        BigDecimal cappedPoints = points == null ? null : applyCapFloor(rule.capFloor(), points);
        BigDecimal cappedMoney = money == null ? null : applyCapFloor(rule.capFloor(), money);
        return new HookComputation(HookStatus.APPLIED, cappedPoints, cappedMoney, formulaInputs);
    }

    private static BigDecimal requireAmount(CompensationHookRule rule, String name) {
        BigDecimal value = rule.formulaParameters().get(name);
        if (value == null) {
            throw new IllegalArgumentException(rule.hookType() + " requires configured formula parameter " + name);
        }
        return value;
    }

    private static BigDecimal applyCapFloor(CapFloor capFloor, BigDecimal value) {
        if (capFloor == null) {
            return value;
        }
        BigDecimal result = value;
        if (capFloor.floor() != null && result.compareTo(capFloor.floor()) < 0) {
            result = capFloor.floor();
        }
        if (capFloor.cap() != null && result.compareTo(capFloor.cap()) > 0) {
            result = capFloor.cap();
        }
        return result;
    }

    private static BigDecimal normalizePoints(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void rejectRawPersonnelIdentifier(String value, String message) {
        if (value.contains(" ") || value.contains("@")) {
            throw new IllegalArgumentException(message);
        }
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

    private record HookComputation(HookStatus status, BigDecimal pointsDelta, BigDecimal moneyAmount, Map<String, String> formulaInputs) {
        private HookComputation {
            Objects.requireNonNull(status, "status is required");
            formulaInputs = Map.copyOf(new TreeMap<>(formulaInputs == null ? Map.of() : formulaInputs));
        }
    }

    public record CompensationHookRequest(
        UUID tenantId,
        String quoteId,
        String scenarioId,
        TokenizedIdentifier loanOfficerToken,
        TokenizedIdentifier branchToken,
        String channel,
        String product,
        String investor,
        BigDecimal loanAmount,
        BigDecimal priceBeforeHooks,
        CompensationPlanSnapshot planSnapshot,
        List<CompensationHookRule> rules,
        int startingWaterfallSequence,
        Instant quoteDate,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey
    ) {
        public CompensationHookRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(quoteId, "quoteId is required");
            requireText(scenarioId, "scenarioId is required");
            Objects.requireNonNull(loanOfficerToken, "loanOfficerToken is required");
            Objects.requireNonNull(branchToken, "branchToken is required");
            requireText(channel, "channel is required");
            requireText(product, "product is required");
            requireText(investor, "investor is required");
            Objects.requireNonNull(loanAmount, "loanAmount is required");
            Objects.requireNonNull(priceBeforeHooks, "priceBeforeHooks is required");
            Objects.requireNonNull(planSnapshot, "planSnapshot is required");
            rules = List.copyOf(rules == null ? List.of() : rules);
            if (startingWaterfallSequence <= 0) {
                throw new IllegalArgumentException("startingWaterfallSequence must be positive");
            }
            Objects.requireNonNull(quoteDate, "quoteDate is required");
            occurredAt = occurredAt == null ? Instant.now() : occurredAt;
            requireText(correlationId, "correlationId is required");
            requireText(idempotencyKey, "idempotencyKey is required");
        }

        private void validate() {
            if (loanAmount.signum() < 0) {
                throw new IllegalArgumentException("loanAmount cannot be negative");
            }
            if (!tenantId.equals(planSnapshot.tenantId())) {
                throw new IllegalArgumentException("compensation hook tenant mismatch");
            }
            if (!planSnapshot.approved()) {
                throw new IllegalStateException("compensation hook requires an approved compensation plan snapshot");
            }
            if (!planSnapshot.effectiveWindow().contains(quoteDate)) {
                throw new IllegalStateException("compensation plan snapshot is not effective for quote date");
            }
            for (CompensationHookRule rule : rules) {
                rule.validateFor(this);
            }
        }
    }

    public record TokenizedIdentifier(String value) {
        public TokenizedIdentifier {
            requireText(value, "tokenized identifier is required");
            rejectRawPersonnelIdentifier(value, "personnel identifiers must be tokenized and must not contain raw names or email addresses");
        }
    }

    public record CompensationPlanSnapshot(
        UUID tenantId,
        String planCode,
        int planVersion,
        boolean approved,
        String snapshotHash,
        EffectiveWindow effectiveWindow
    ) {
        public CompensationPlanSnapshot {
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(planCode, "planCode is required");
            if (planVersion <= 0) {
                throw new IllegalArgumentException("planVersion must be positive");
            }
            requireText(snapshotHash, "snapshotHash is required");
            Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
        }

        public String planRef() {
            return planCode + ":" + planVersion;
        }
    }

    public record CompensationHookRule(
        String mappingId,
        UUID tenantId,
        String planCode,
        int planVersion,
        HookType hookType,
        Map<String, String> selectors,
        Map<String, BigDecimal> formulaParameters,
        CapFloor capFloor,
        VisibilityPolicy visibilityPolicy,
        String reasonCode,
        int priority,
        MappingStatus status,
        EffectiveWindow effectiveWindow,
        String requestedByToken,
        String approvedByToken,
        String mappingContentHash
    ) {
        public CompensationHookRule {
            requireText(mappingId, "mappingId is required");
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(planCode, "planCode is required");
            if (planVersion <= 0) {
                throw new IllegalArgumentException("planVersion must be positive");
            }
            Objects.requireNonNull(hookType, "hookType is required");
            selectors = Map.copyOf(new TreeMap<>(selectors == null ? Map.of() : selectors));
            formulaParameters = Map.copyOf(new TreeMap<>(formulaParameters == null ? Map.of() : formulaParameters));
            visibilityPolicy = visibilityPolicy == null ? VisibilityPolicy.summary("Compensation") : visibilityPolicy;
            requireText(reasonCode, "reasonCode is required");
            if (priority < 0) {
                throw new IllegalArgumentException("priority cannot be negative");
            }
            Objects.requireNonNull(status, "status is required");
            Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
            requireText(requestedByToken, "requestedByToken is required");
            requireText(approvedByToken, "approvedByToken is required");
            rejectRawPersonnelIdentifier(requestedByToken, "requestedByToken must be tokenized and must not contain raw names or email addresses");
            rejectRawPersonnelIdentifier(approvedByToken, "approvedByToken must be tokenized and must not contain raw names or email addresses");
            if (requestedByToken.equals(approvedByToken)) {
                throw new IllegalArgumentException("compensation hook mapping approval requires separation of duties");
            }
            requireText(mappingContentHash, "mappingContentHash is required");
        }

        private void validateFor(CompensationHookRequest request) {
            if (!tenantId.equals(request.tenantId())) {
                throw new IllegalArgumentException("compensation hook rule tenant mismatch");
            }
            if (!planCode.equals(request.planSnapshot().planCode()) || planVersion != request.planSnapshot().planVersion()) {
                throw new IllegalArgumentException("compensation hook rule plan snapshot mismatch");
            }
            if (status != MappingStatus.APPROVED) {
                throw new IllegalStateException("compensation hook mapping must be approved before evaluation");
            }
            if (!effectiveWindow.contains(request.quoteDate())) {
                throw new IllegalStateException("compensation hook mapping is not effective for quote date");
            }
        }

        private boolean matches(CompensationHookRequest request) {
            return selectorMatches("channel", request.channel())
                && selectorMatches("product", request.product())
                && selectorMatches("investor", request.investor());
        }

        private boolean selectorMatches(String name, String value) {
            String expected = selectors.get(name);
            return expected == null || "*".equals(expected) || expected.equals(value);
        }
    }

    public record CapFloor(BigDecimal floor, BigDecimal cap) {
        public CapFloor {
            if (floor != null && cap != null && floor.compareTo(cap) > 0) {
                throw new IllegalArgumentException("cap/floor floor cannot exceed cap");
            }
        }
    }

    public record VisibilityPolicy(boolean detailVisible, String visibleLabel, String summaryLabel) {
        public VisibilityPolicy {
            requireText(visibleLabel, "visibleLabel is required");
            requireText(summaryLabel, "summaryLabel is required");
        }

        public static VisibilityPolicy summary(String label) {
            return new VisibilityPolicy(false, label, label);
        }

        private String labelForResult() {
            return detailVisible ? visibleLabel : summaryLabel;
        }
    }

    public record EffectiveWindow(Instant effectiveStart, Instant effectiveEnd) {
        public EffectiveWindow {
            Objects.requireNonNull(effectiveStart, "effectiveStart is required");
            if (effectiveEnd != null && effectiveEnd.isBefore(effectiveStart)) {
                throw new IllegalArgumentException("effectiveEnd cannot be before effectiveStart");
            }
        }

        private boolean contains(Instant instant) {
            Objects.requireNonNull(instant, "instant is required");
            return !instant.isBefore(effectiveStart) && (effectiveEnd == null || instant.isBefore(effectiveEnd));
        }
    }

    public enum HookType {
        PRICE_POINTS_DELTA,
        BPS_DELTA,
        FIXED_MONEY_FEE,
        PERCENT_OF_LOAN_AMOUNT,
        FEE_CREDIT,
        LABEL_ONLY,
        BLOCKING_POLICY
    }

    public enum MappingStatus {
        DRAFT,
        APPROVED,
        RETIRED
    }

    public enum HookStatus {
        APPLIED,
        BLOCKED
    }

    public record CompensationHookResult(
        UUID hookLedgerId,
        String mappingId,
        HookType hookType,
        HookStatus status,
        BigDecimal pointsDelta,
        BigDecimal moneyAmount,
        Map<String, String> formulaInputs,
        String visibilityLabel,
        String reasonCode,
        int waterfallSequence,
        String inputSnapshotHash
    ) {
        public CompensationHookResult {
            Objects.requireNonNull(hookLedgerId, "hookLedgerId is required");
            requireText(mappingId, "mappingId is required");
            Objects.requireNonNull(hookType, "hookType is required");
            Objects.requireNonNull(status, "status is required");
            formulaInputs = Map.copyOf(new TreeMap<>(formulaInputs == null ? Map.of() : formulaInputs));
            requireText(visibilityLabel, "visibilityLabel is required");
            requireText(reasonCode, "reasonCode is required");
            if (waterfallSequence <= 0) {
                throw new IllegalArgumentException("waterfallSequence must be positive");
            }
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
        }
    }

    public record CompensationHookEvaluation(
        List<CompensationHookResult> hookResults,
        String inputSnapshotHash,
        CompensationHookEvent event,
        CompensationHookAudit audit
    ) {
        public CompensationHookEvaluation {
            hookResults = List.copyOf(hookResults == null ? List.of() : hookResults);
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            Objects.requireNonNull(event, "event is required");
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record CompensationHookEvent(
        String eventType,
        int eventVersion,
        UUID eventId,
        String sourceService,
        String key,
        UUID tenantId,
        String quoteId,
        String scenarioId,
        String planRef,
        List<String> mappingIds,
        String resultHash,
        String correlationId,
        String idempotencyKey,
        Instant occurredAt
    ) {
        public CompensationHookEvent {
            requireText(eventType, "eventType is required");
            Objects.requireNonNull(eventId, "eventId is required");
            requireText(sourceService, "sourceService is required");
            requireText(key, "key is required");
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(quoteId, "quoteId is required");
            requireText(scenarioId, "scenarioId is required");
            requireText(planRef, "planRef is required");
            mappingIds = List.copyOf(mappingIds == null ? List.of() : mappingIds);
            requireText(resultHash, "resultHash is required");
            requireText(correlationId, "correlationId is required");
            requireText(idempotencyKey, "idempotencyKey is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
        }
    }

    public record CompensationHookAudit(
        UUID tenantId,
        String action,
        String actorToken,
        String correlationId,
        String planSnapshotHash,
        String inputSnapshotHash,
        String resultHash
    ) {
        public CompensationHookAudit {
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(action, "action is required");
            requireText(actorToken, "actorToken is required");
            requireText(correlationId, "correlationId is required");
            requireText(planSnapshotHash, "planSnapshotHash is required");
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            requireText(resultHash, "resultHash is required");
        }
    }
}
