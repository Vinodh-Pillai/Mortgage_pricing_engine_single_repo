package com.wcpe.pricing.parrate;

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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ParRateIdentificationApi {
    public static final String PAR_RATE_WRITE_PERMISSION = "pricing.par-rate.calculate";
    public static final String PAR_RATE_READ_PERMISSION = "pricing.par-rate.read";

    private static final int INTERMEDIATE_SCALE = 8;
    private static final int PERSISTED_SCALE = 5;

    private final ParRateIdentificationRepository repository;

    public ParRateIdentificationApi(ParRateIdentificationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public ParRateIdentificationResponse identify(String tenantId, ParRateHeaders headers,
            ParRateIdentificationRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, PAR_RATE_WRITE_PERMISSION);
        validateRequest(request);

        if (request.requestedNoteRate() != null && request.candidateRates().stream()
                .noneMatch(candidate -> candidate.lockPeriodDays() == request.lockPeriodDays()
                        && rateScale(candidate.noteRate()).compareTo(rateScale(request.requestedNoteRate())) == 0)) {
            throw new ParRateIdentificationException(ParRateErrorCode.REQUESTED_RATE_NOT_IN_GRID,
                    "requested rate is not available in the lock-period grid slice");
        }

        ParPolicyVersion policy = repository.findPublishedPolicy(tenantId, request.productCode(),
                        request.investorCode(), request.channelCode(), request.parPolicyVersionId(), request.asOf())
                .orElseThrow(() -> new ParRateIdentificationException(ParRateErrorCode.PAR_POLICY_MISSING,
                        "published par policy is required"));
        if (policy.targetPrice() == null || policy.comparator() == null || policy.priceBasis() == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_POLICY_MISSING,
                    "par policy target, comparator, and price basis are required");
        }

        List<ParCandidateRate> gridSlice = request.candidateRates().stream()
                .filter(candidate -> candidate.lockPeriodDays() == request.lockPeriodDays())
                .sorted(Comparator.comparing(ParCandidateRate::noteRate))
                .toList();
        if (gridSlice.isEmpty()) {
            throw new ParRateIdentificationException(ParRateErrorCode.LOCK_PERIOD_UNSUPPORTED,
                    "no candidates exist for the requested lock period");
        }

        List<CandidateEvaluation> evaluations = evaluateCandidates(gridSlice, policy);
        BigDecimal bestDistance = evaluations.stream()
                .map(CandidateEvaluation::distanceToTarget)
                .min(BigDecimal::compareTo)
                .orElseThrow();
        List<CandidateEvaluation> bestMatches = evaluations.stream()
                .filter(evaluation -> evaluation.distanceToTarget().compareTo(bestDistance) == 0)
                .toList();

        CandidateEvaluation parEvaluation = selectParCandidate(policy, bestMatches);
        List<CandidateEvaluation> finalEvaluations = evaluations.stream()
                .map(evaluation -> evaluation.noteRate().compareTo(parEvaluation.noteRate()) == 0
                        ? evaluation.asParCandidate(policy.tieBreaker())
                        : evaluation)
                .toList();

        List<ParLedgerEntry> ledger = List.of(
                new ParLedgerEntry(1, "POLICY_RESOLUTION", policy.id(), null, "PAR_POLICY_RESOLVED"),
                new ParLedgerEntry(2, "GRID_SLICE", request.gridVersionId().toString(),
                        BigDecimal.valueOf(gridSlice.size()), "LOCK_PERIOD_SCOPED"),
                new ParLedgerEntry(3, "PAR_COMPARISON", policy.comparator().name(), parEvaluation.distanceToTarget(),
                        parEvaluation.reasonCode()));

        String resultHash = stableHash("par-rate", tenantId, request.scenarioHash(), request.gridVersionId(),
                request.lockPeriodDays(), policy.id(), finalEvaluations, ledger);
        UUID id = UUID.nameUUIDFromBytes((tenantId + ":" + resultHash).getBytes(StandardCharsets.UTF_8));
        String cacheKey = "pricing:par-rate:%s:%s:%s:%s:%s".formatted(
                tenantId, request.scenarioHash(), request.gridVersionId(), request.lockPeriodDays(), policy.id());
        ParRateIdentificationResponse response = new ParRateIdentificationResponse(id, parEvaluation.noteRate(),
                parEvaluation.evaluatedPrice(), policy.id(), policy.priceBasis(), finalEvaluations, ledger, resultHash,
                cacheKey);

        repository.save(new ParRateIdentificationResult(id, tenantId, request.gridVersionId(), request.lockPeriodDays(),
                policy.id(), response, resultHash, headers.actorId(), headers.correlationId(), Instant.now()));
        repository.saveEvent(new ParRateEvent("pricing.par-rate-identified.v1", tenantId + ":" + id, tenantId, id,
                policy.id(), response.parNoteRate(), response.parPrice(), resultHash, headers.correlationId()));
        repository.saveAudit(new ParRateAudit("PAR_RATE_IDENTIFICATION_COMPLETED", tenantId, id, headers.actorId(),
                headers.correlationId(), List.of(policy.id(), request.gridVersionId().toString()), resultHash));
        return response;
    }

    public ParRateIdentificationResponse get(String tenantId, UUID id, ParRateHeaders headers) {
        requireTenant(tenantId);
        requirePermission(headers, PAR_RATE_READ_PERMISSION);
        if (id == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_CANDIDATES_EMPTY,
                    "par_identification_id is required");
        }
        ParRateIdentificationResult result = repository.findById(id)
                .orElseThrow(() -> new ParRateIdentificationException(ParRateErrorCode.PAR_CANDIDATES_EMPTY,
                        "par rate identification result was not found"));
        if (!tenantId.equals(result.tenantId())) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_CANDIDATES_EMPTY,
                    "par rate identification tenant does not match request tenant");
        }
        return result.response();
    }

    public void handlePolicyPublished(String tenantId, String scope, String policyVersionId) {
        requireTenant(tenantId);
        requireText(policyVersionId, "policy_version_id is required");
        repository.invalidatePolicyCache("pricing:par-policy:%s:%s:%s".formatted(tenantId,
                scope == null || scope.isBlank() ? "default" : scope, policyVersionId));
    }

    private static List<CandidateEvaluation> evaluateCandidates(List<ParCandidateRate> candidates, ParPolicyVersion policy) {
        List<CandidateEvaluation> evaluations = new ArrayList<>();
        for (ParCandidateRate candidate : candidates) {
            BigDecimal evaluatedPrice = policy.priceBasis() == PriceBasis.FINAL
                    ? priceScale(candidate.finalPrice())
                    : priceScale(candidate.basePrice());
            BigDecimal distance = switch (policy.comparator()) {
                case NEAREST_TO_TARGET -> evaluatedPrice.subtract(priceScale(policy.targetPrice())).abs()
                        .setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
                case EXACT_TARGET -> evaluatedPrice.compareTo(priceScale(policy.targetPrice())) == 0
                        ? BigDecimal.ZERO.setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                        : evaluatedPrice.subtract(priceScale(policy.targetPrice())).abs()
                                .setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
            };
            evaluations.add(new CandidateEvaluation(candidate.noteRate(), evaluatedPrice, distance, false,
                    candidate.rowRef(), "EVALUATED_AGAINST_CONFIGURED_POLICY"));
        }
        if (policy.comparator() == ParComparator.EXACT_TARGET
                && evaluations.stream().noneMatch(evaluation -> evaluation.distanceToTarget().signum() == 0)) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_CANDIDATES_EMPTY,
                    "no candidate satisfies the configured exact par target");
        }
        return evaluations;
    }

    private static CandidateEvaluation selectParCandidate(ParPolicyVersion policy, List<CandidateEvaluation> bestMatches) {
        if (bestMatches.size() == 1) {
            return bestMatches.get(0).asParCandidate("SINGLE_BEST_MATCH");
        }
        if (policy.tieBreaker() == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_TIE_UNRESOLVED,
                    "multiple candidates tie and no configured tie-breaker is available");
        }
        return switch (policy.tieBreaker()) {
            case LOWEST_NOTE_RATE -> bestMatches.stream().min(Comparator.comparing(CandidateEvaluation::noteRate)).orElseThrow()
                    .asParCandidate(policy.tieBreaker().name());
            case HIGHEST_NOTE_RATE -> bestMatches.stream().max(Comparator.comparing(CandidateEvaluation::noteRate)).orElseThrow()
                    .asParCandidate(policy.tieBreaker().name());
        };
    }

    private static void validateRequest(ParRateIdentificationRequest request) {
        if (request == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_CANDIDATES_EMPTY, "request is required");
        }
        requireText(request.scenarioHash(), "scenario_hash is required");
        requireText(request.productCode(), "product_code is required");
        requireText(request.investorCode(), "investor_code is required");
        requireText(request.channelCode(), "channel_code is required");
        if (request.gridVersionId() == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.GRID_VERSION_STALE, "grid_version_id is required");
        }
        if (request.lockPeriodDays() <= 0) {
            throw new ParRateIdentificationException(ParRateErrorCode.LOCK_PERIOD_UNSUPPORTED,
                    "lock_period_days must be positive");
        }
        if (request.asOf() == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_POLICY_MISSING, "as_of is required");
        }
        if (request.candidateRates().isEmpty()) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_CANDIDATES_EMPTY,
                    "candidate rates are required");
        }
    }

    private static void requirePermission(ParRateHeaders headers, String permission) {
        if (headers == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_POLICY_MISSING, "headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!PAR_RATE_READ_PERMISSION.equals(permission)) {
            requireText(headers.idempotencyKey(), "idempotency_key is required");
        }
        if (!headers.permissions().contains(permission)) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_POLICY_MISSING,
                    permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_POLICY_MISSING, message);
        }
    }

    private static BigDecimal rateScale(BigDecimal value) {
        if (value == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_CANDIDATES_EMPTY, "note_rate is required");
        }
        return value.setScale(PERSISTED_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal priceScale(BigDecimal value) {
        if (value == null) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_CANDIDATES_EMPTY, "price is required");
        }
        return value.setScale(PERSISTED_SCALE, RoundingMode.HALF_UP);
    }

    private static String stableHash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public enum ParComparator {
        NEAREST_TO_TARGET,
        EXACT_TARGET
    }

    public enum ParTieBreaker {
        LOWEST_NOTE_RATE,
        HIGHEST_NOTE_RATE
    }

    public enum PriceBasis {
        BASE,
        FINAL
    }

    public enum ParPolicyStatus {
        DRAFT,
        PUBLISHED,
        RETIRED
    }

    public enum ParRateErrorCode {
        PAR_POLICY_MISSING,
        PAR_CANDIDATES_EMPTY,
        PAR_TIE_UNRESOLVED,
        LOCK_PERIOD_UNSUPPORTED,
        GRID_VERSION_STALE,
        REQUESTED_RATE_NOT_IN_GRID
    }

    public record ParRateHeaders(Set<String> permissions, String actorId, String correlationId, String idempotencyKey) {
        public ParRateHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record ParRateIdentificationRequest(
            String scenarioHash,
            UUID gridVersionId,
            String productCode,
            String investorCode,
            String channelCode,
            int lockPeriodDays,
            List<ParCandidateRate> candidateRates,
            BigDecimal requestedNoteRate,
            String parPolicyVersionId,
            Instant asOf) {
        public ParRateIdentificationRequest {
            candidateRates = candidateRates == null ? List.of() : List.copyOf(candidateRates);
        }
    }

    public record ParCandidateRate(
            BigDecimal noteRate,
            int lockPeriodDays,
            BigDecimal basePrice,
            BigDecimal finalPrice,
            String rowRef) {
        public ParCandidateRate {
            noteRate = rateScale(noteRate);
            basePrice = priceScale(basePrice);
            finalPrice = finalPrice == null ? null : priceScale(finalPrice);
            rowRef = rowRef == null || rowRef.isBlank() ? "unreferenced-row" : rowRef;
        }
    }

    public record ParPolicyVersion(
            String id,
            String tenantId,
            String productCode,
            String investorCode,
            String channelCode,
            ParPolicyStatus status,
            Instant effectiveFrom,
            Instant effectiveTo,
            BigDecimal targetPrice,
            ParComparator comparator,
            ParTieBreaker tieBreaker,
            PriceBasis priceBasis,
            String roundingPolicyRef) {
        public ParPolicyVersion {
            requireText(id, "policy id is required");
            requireText(tenantId, "policy tenant_id is required");
            targetPrice = targetPrice == null ? null : priceScale(targetPrice);
        }

        boolean matches(String tenantId, String productCode, String investorCode, String channelCode,
                String requestedPolicyVersionId, Instant asOf) {
            return Objects.equals(this.tenantId, tenantId)
                    && Objects.equals(this.productCode, productCode)
                    && Objects.equals(this.investorCode, investorCode)
                    && Objects.equals(this.channelCode, channelCode)
                    && (requestedPolicyVersionId == null || requestedPolicyVersionId.isBlank()
                            || Objects.equals(id, requestedPolicyVersionId))
                    && status == ParPolicyStatus.PUBLISHED
                    && (effectiveFrom == null || effectiveFrom.isBefore(asOf) || effectiveFrom.equals(asOf))
                    && (effectiveTo == null || asOf.isBefore(effectiveTo));
        }
    }

    public record CandidateEvaluation(
            BigDecimal noteRate,
            BigDecimal evaluatedPrice,
            BigDecimal distanceToTarget,
            boolean parCandidate,
            String rowRef,
            String reasonCode) {
        public CandidateEvaluation {
            noteRate = rateScale(noteRate);
            evaluatedPrice = priceScale(evaluatedPrice);
            distanceToTarget = distanceToTarget.setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        }

        CandidateEvaluation asParCandidate(Object reason) {
            return new CandidateEvaluation(noteRate, evaluatedPrice, distanceToTarget, true, rowRef,
                    "PAR_SELECTED_" + reason);
        }
    }

    public record ParLedgerEntry(int ordinal, String step, String ref, BigDecimal value, String reasonCode) {
        public ParLedgerEntry {
            value = value == null ? null : value.setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        }
    }

    public record ParRateIdentificationResponse(
            UUID parIdentificationId,
            BigDecimal parNoteRate,
            BigDecimal parPrice,
            String parPolicyVersionId,
            PriceBasis priceBasis,
            List<CandidateEvaluation> candidateEvaluations,
            List<ParLedgerEntry> ledger,
            String resultHash,
            String cacheKey) {
        public ParRateIdentificationResponse {
            parNoteRate = rateScale(parNoteRate);
            parPrice = priceScale(parPrice);
            candidateEvaluations = candidateEvaluations == null ? List.of() : List.copyOf(candidateEvaluations);
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
        }
    }

    public record ParRateIdentificationResult(
            UUID id,
            String tenantId,
            UUID gridVersionId,
            int lockPeriodDays,
            String parPolicyVersionId,
            ParRateIdentificationResponse response,
            String resultHash,
            String actorId,
            String correlationId,
            Instant createdAt) {
    }

    public record ParRateEvent(String eventType, String eventKey, String tenantId, UUID parIdentificationId,
            String parPolicyVersionId, BigDecimal parNoteRate, BigDecimal parPrice, String resultHash,
            String correlationId) {
        public ParRateEvent {
            parNoteRate = rateScale(parNoteRate);
            parPrice = priceScale(parPrice);
        }
    }

    public record ParRateAudit(String action, String tenantId, UUID parIdentificationId, String actorId,
            String correlationId, List<String> versionRefs, String resultHash) {
        public ParRateAudit {
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
        }
    }

    public interface ParRateIdentificationRepository {
        Optional<ParPolicyVersion> findPublishedPolicy(String tenantId, String productCode, String investorCode,
                String channelCode, String policyVersionId, Instant asOf);

        void save(ParRateIdentificationResult result);

        Optional<ParRateIdentificationResult> findById(UUID id);

        void saveEvent(ParRateEvent event);

        void saveAudit(ParRateAudit audit);

        void invalidatePolicyCache(String cacheKey);
    }

    public static final class InMemoryParRateIdentificationRepository implements ParRateIdentificationRepository {
        private final List<ParPolicyVersion> policies = new ArrayList<>();
        private final Map<UUID, ParRateIdentificationResult> results = new ConcurrentHashMap<>();
        private final List<ParRateEvent> events = new ArrayList<>();
        private final List<ParRateAudit> audits = new ArrayList<>();
        private final Set<String> invalidatedPolicyCacheKeys = ConcurrentHashMap.newKeySet();

        public void addPolicy(ParPolicyVersion policy) {
            policies.add(Objects.requireNonNull(policy));
        }

        @Override
        public Optional<ParPolicyVersion> findPublishedPolicy(String tenantId, String productCode, String investorCode,
                String channelCode, String policyVersionId, Instant asOf) {
            List<ParPolicyVersion> matching = policies.stream()
                    .filter(policy -> policy.matches(tenantId, productCode, investorCode, channelCode, policyVersionId, asOf))
                    .toList();
            if (matching.size() > 1) {
                throw new ParRateIdentificationException(ParRateErrorCode.PAR_POLICY_MISSING,
                        "ambiguous published par policy versions");
            }
            return matching.stream().findFirst();
        }

        @Override
        public void save(ParRateIdentificationResult result) {
            results.put(result.id(), result);
        }

        @Override
        public Optional<ParRateIdentificationResult> findById(UUID id) {
            return Optional.ofNullable(results.get(id));
        }

        @Override
        public void saveEvent(ParRateEvent event) {
            events.add(event);
        }

        @Override
        public void saveAudit(ParRateAudit audit) {
            audits.add(audit);
        }

        @Override
        public void invalidatePolicyCache(String cacheKey) {
            invalidatedPolicyCacheKeys.add(cacheKey);
        }

        public List<ParRateEvent> events() {
            return List.copyOf(events);
        }

        public List<ParRateAudit> audits() {
            return List.copyOf(audits);
        }

        public boolean wasPolicyCacheInvalidated(String cacheKey) {
            return invalidatedPolicyCacheKeys.contains(cacheKey);
        }
    }

    public static class ParRateIdentificationException extends RuntimeException {
        private final ParRateErrorCode code;

        public ParRateIdentificationException(ParRateErrorCode code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code);
        }

        public ParRateErrorCode code() {
            return code;
        }
    }
}
