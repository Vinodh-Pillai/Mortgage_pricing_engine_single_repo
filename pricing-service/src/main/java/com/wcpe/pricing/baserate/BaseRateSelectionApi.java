package com.wcpe.pricing.baserate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseRateSelectionApi {
    public static final String BASE_RATE_READ_PERMISSION = "pricing.baserate.read";
    public static final String BASE_RATE_WRITE_PERMISSION = "pricing.baserate.write";

    private final BaseRateSelectionRepository repository;

    public BaseRateSelectionApi(BaseRateSelectionRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public BaseRateSelectionResponse selectRate(String tenantId, BaseRateSelectionHeaders headers, BaseRateSelectionRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, BASE_RATE_WRITE_PERMISSION);
        validateSelectionRequest(tenantId, request);

        UUID gridVersionId = resolveGridVersionId(tenantId, request.productCode(), request.investorCode(),
                request.channelCode(), request.asOf());

        List<BasePricingGridRow> matchingRows = repository.findGridRows(tenantId, gridVersionId, request.lockPeriodDays());
        if (matchingRows.isEmpty()) {
            throw new BaseRateSelectionNotFoundException("no grid rows found for lock period " + request.lockPeriodDays());
        }

        List<CandidateRate> candidates = matchingRows.stream()
                .sorted(Comparator.comparing(BasePricingGridRow::noteRate))
                .map(row -> new CandidateRate(row.noteRate(), row.basePrice(), 0, "GRID_MATCH"))
                .toList();

        int finalRank = 1;
        List<CandidateRate> rankedCandidates = new ArrayList<>();
        for (CandidateRate candidate : candidates) {
            rankedCandidates.add(new CandidateRate(candidate.noteRate(), candidate.basePrice(), finalRank++, candidate.reasonCode()));
        }

        CandidateRate selected = applySelectionPolicy(rankedCandidates, request.requestedNoteRate(), request.selectionPolicyId());

        List<LedgerEntry> ledger = new ArrayList<>();
        ledger.add(new LedgerEntry("GRID_RESOLUTION", "Resolved grid version " + gridVersionId, null, "GRID_RESOLVED"));
        ledger.add(new LedgerEntry("CANDIDATE_GENERATION", "Generated " + rankedCandidates.size() + " candidates", BigDecimal.valueOf(rankedCandidates.size()), "CANDIDATES_GENERATED"));
        ledger.add(new LedgerEntry("RATE_SELECTION", "Selected rate " + selected.noteRate() + " at price " + selected.basePrice(), selected.basePrice(), selected.reasonCode()));

        List<String> warnings = new ArrayList<>();

        String resultHash = UUID.randomUUID().toString();

        BaseRateSelection selection = new BaseRateSelection(
                UUID.randomUUID(),
                gridVersionId,
                selected.noteRate(),
                selected.basePrice(),
                rankedCandidates,
                request.lockPeriodDays(),
                request.asOf(),
                ledger,
                warnings,
                resultHash,
                BaseRateSelectionStatus.COMPLETED);
        repository.save(selection);

        BaseRateSelectionAudit audit = new BaseRateSelectionAudit(
                UUID.randomUUID(),
                tenantId,
                selection.selectionId(),
                gridVersionId,
                request.scenarioHash(),
                resultHash,
                headers.actorId(),
                headers.correlationId(),
                headers.correlationId(),
                Instant.now());
        repository.saveAudit(audit);

        return new BaseRateSelectionResponse(
                selection.selectionId(),
                gridVersionId,
                selected.noteRate(),
                selected.basePrice(),
                rankedCandidates,
                request.lockPeriodDays(),
                request.asOf(),
                ledger,
                warnings,
                resultHash);
    }

    public BasePricingGridVersion resolveGridVersion(String tenantId, String productCode, String investorCode,
            String channelCode, Instant asOf) {
        requireTenant(tenantId);
        requireText(productCode, "product_code is required");

        List<BasePricingGridVersion> matching = repository.findGridVersion(tenantId, productCode, investorCode, channelCode);
        List<BasePricingGridVersion> effective = matching.stream()
                .filter(v -> v.status() == GridVersionStatus.PUBLISHED)
                .filter(v -> v.effectiveFrom().isBefore(asOf) || v.effectiveFrom().equals(asOf))
                .filter(v -> v.effectiveTo() == null || asOf.isBefore(v.effectiveTo()))
                .toList();

        if (effective.isEmpty()) {
            throw new BaseRateSelectionGridNotFoundException("no published grid version found for tenant " + tenantId);
        }
        if (effective.size() > 1) {
            throw new BaseRateSelectionConflictException("ambiguous grid versions found for tenant " + tenantId);
        }
        return effective.get(0);
    }

    private UUID resolveGridVersionId(String tenantId, String productCode, String investorCode,
            String channelCode, Instant asOf) {
        requireText(productCode, "product_code is required");

        List<BasePricingGridVersion> matching = repository.findGridVersion(tenantId, productCode, investorCode, channelCode);
        List<BasePricingGridVersion> effective = matching.stream()
                .filter(v -> v.status() == GridVersionStatus.PUBLISHED)
                .filter(v -> v.effectiveFrom().isBefore(asOf) || v.effectiveFrom().equals(asOf))
                .filter(v -> v.effectiveTo() == null || asOf.isBefore(v.effectiveTo()))
                .toList();

        if (effective.isEmpty()) {
            throw new BaseRateSelectionGridNotFoundException("no published grid version found for product " + productCode);
        }
        if (effective.size() > 1) {
            throw new BaseRateSelectionConflictException("ambiguous grid versions for product " + productCode);
        }
        return effective.get(0).id();
    }

    private static CandidateRate applySelectionPolicy(List<CandidateRate> candidates, BigDecimal requestedNoteRate,
            String selectionPolicyId) {
        if (candidates.isEmpty()) {
            throw new BaseRateSelectionValidationException("no candidates available for selection");
        }

        if (requestedNoteRate == null) {
            return candidates.get(0);
        }

        Optional<CandidateRate> exactMatch = candidates.stream()
                .filter(c -> c.noteRate().compareTo(requestedNoteRate) == 0)
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        CandidateRate closest = candidates.get(0);
        BigDecimal closestDiff = requestedNoteRate.subtract(closest.noteRate()).abs();
        for (CandidateRate candidate : candidates) {
            BigDecimal diff = requestedNoteRate.subtract(candidate.noteRate()).abs();
            if (diff.compareTo(closestDiff) < 0) {
                closest = candidate;
                closestDiff = diff;
            }
        }
        return closest;
    }

    private static void validateSelectionRequest(String tenantId, BaseRateSelectionRequest request) {
        if (request == null) {
            throw new BaseRateSelectionValidationException("request is required");
        }
        requireText(request.scenarioId(), "scenario_id is required");
        requireText(request.scenarioHash(), "scenario_hash is required");
        requireText(request.productCode(), "product_code is required");
        requireText(request.investorCode(), "investor_code is required");
        requireText(request.channelCode(), "channel_code is required");
        if (request.lockPeriodDays() <= 0) {
            throw new BaseRateSelectionValidationException("lock_period_days must be positive");
        }
        if (request.asOf() == null) {
            throw new BaseRateSelectionValidationException("as_of is required");
        }
    }

    private static void requirePermission(BaseRateSelectionHeaders headers, String permission) {
        if (headers == null) {
            throw new BaseRateSelectionAccessDeniedException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        requireText(headers.idempotencyKey(), "idempotency_key is required");
        if (!headers.permissions().contains(permission)) {
            throw new BaseRateSelectionAccessDeniedException(permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BaseRateSelectionValidationException(message);
        }
    }

    // ── Enums ──

    public enum GridVersionStatus {
        DRAFT,
        PUBLISHED,
        SUSPENDED
    }

    public enum BaseRateSelectionStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REJECTED
    }

    // ── Value Objects ──

    public record TenantId(String value) {
        public TenantId {
            if (value == null || value.isBlank()) {
                throw new BaseRateSelectionValidationException("tenant_id must not be blank");
            }
        }
    }

    public record ProductCode(String value) {
        public ProductCode {
            if (value == null || value.isBlank()) {
                throw new BaseRateSelectionValidationException("product_code must not be blank");
            }
        }
    }

    public record InvestorCode(String value) {
        public InvestorCode {
            if (value == null || value.isBlank()) {
                throw new BaseRateSelectionValidationException("investor_code must not be blank");
            }
        }
    }

    public record ChannelCode(String value) {
        public ChannelCode {
            if (value == null || value.isBlank()) {
                throw new BaseRateSelectionValidationException("channel_code must not be blank");
            }
        }
    }

    public record LockPeriodDays(int value) {
        public LockPeriodDays {
            if (value <= 0) {
                throw new BaseRateSelectionValidationException("lock_period_days must be positive");
            }
        }
    }

    public record NoteRate(BigDecimal value) {
        public NoteRate {
            if (value == null) {
                throw new BaseRateSelectionValidationException("note_rate must not be null");
            }
            this.value = value.setScale(5, BigDecimal.ROUND_HALF_UP);
        }
    }

    public record Price(BigDecimal value) {
        public Price {
            if (value == null) {
                throw new BaseRateSelectionValidationException("price must not be null");
            }
            this.value = value.setScale(5, BigDecimal.ROUND_HALF_UP);
        }
    }

    public record AsOfInstant(Instant value) {
        public AsOfInstant {
            if (value == null) {
                throw new BaseRateSelectionValidationException("as_of must not be null");
            }
        }
    }

    public record ScenarioHash(String value) {
        public ScenarioHash {
            if (value == null || value.isBlank()) {
                throw new BaseRateSelectionValidationException("scenario_hash must not be blank");
            }
        }
    }

    public record PricingVersionRef(String value) {
        public PricingVersionRef {
            if (value == null || value.isBlank()) {
                throw new BaseRateSelectionValidationException("pricing_version_ref must not be blank");
            }
        }
    }

    // ── Domain Records ──

    public record BasePricingGridVersion(
            UUID id,
            String tenantId,
            String productCode,
            String investorCode,
            String channelCode,
            int versionNumber,
            GridVersionStatus status,
            Instant effectiveFrom,
            Instant effectiveTo,
            String sourceDigest,
            String approvedBy,
            Instant approvedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record BasePricingGridRow(
            UUID id,
            String tenantId,
            UUID gridVersionId,
            int lockPeriodDays,
            BigDecimal noteRate,
            BigDecimal basePrice,
            Map<String, String> bucketKey,
            String rowHash,
            Instant createdAt) {
        public BasePricingGridRow {
            if (noteRate != null) {
                noteRate = noteRate.setScale(5, BigDecimal.ROUND_HALF_UP);
            }
            if (basePrice != null) {
                basePrice = basePrice.setScale(5, BigDecimal.ROUND_HALF_UP);
            }
        }
    }

    public record BaseRateSelection(
            UUID selectionId,
            UUID gridVersionId,
            BigDecimal selectedNoteRate,
            BigDecimal selectedBasePrice,
            List<CandidateRate> candidateRates,
            int lockPeriodDays,
            Instant asOf,
            List<LedgerEntry> ledger,
            List<String> warnings,
            String resultHash,
            BaseRateSelectionStatus status) {
        public BaseRateSelection {
            candidateRates = candidateRates == null ? List.of() : List.copyOf(candidateRates);
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record CandidateRate(
            BigDecimal noteRate,
            BigDecimal basePrice,
            int rank,
            String reasonCode) {
        public CandidateRate {
            if (noteRate != null) {
                noteRate = noteRate.setScale(5, BigDecimal.ROUND_HALF_UP);
            }
            if (basePrice != null) {
                basePrice = basePrice.setScale(5, BigDecimal.ROUND_HALF_UP);
            }
        }
    }

    public record LedgerEntry(
            String step,
            String detail,
            BigDecimal value,
            String reasonCode) {
    }

    public record BaseRateSelectionRequest(
            String scenarioId,
            String scenarioHash,
            String productCode,
            String investorCode,
            String channelCode,
            int lockPeriodDays,
            Instant asOf,
            BigDecimal requestedNoteRate,
            String selectionPolicyId) {
    }

    public record BaseRateSelectionResponse(
            UUID selectionId,
            UUID gridVersionId,
            BigDecimal selectedNoteRate,
            BigDecimal selectedBasePrice,
            List<CandidateRate> candidateRates,
            int lockPeriodDays,
            Instant asOf,
            List<LedgerEntry> ledger,
            List<String> warnings,
            String resultHash) {
        public BaseRateSelectionResponse {
            candidateRates = candidateRates == null ? List.of() : List.copyOf(candidateRates);
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record BaseRateSelectionHeaders(
            Set<String> permissions,
            String actorId,
            String correlationId,
            String idempotencyKey) {
        public BaseRateSelectionHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record BaseRateSelectionAudit(
            UUID auditId,
            String tenantId,
            UUID selectionId,
            UUID gridVersionId,
            String requestHash,
            String responseHash,
            String actorId,
            String correlationId,
            String causationId,
            Instant occurredAt) {
    }

    // ── Repository ──

    public interface BaseRateSelectionRepository {
        void save(BaseRateSelection selection);

        List<BasePricingGridVersion> findGridVersion(String tenantId, String productCode, String investorCode, String channelCode);

        List<BasePricingGridRow> findGridRows(String tenantId, UUID gridVersionId, int lockPeriodDays);

        void saveAudit(BaseRateSelectionAudit audit);
    }

    public static final class InMemoryBaseRateSelectionRepository implements BaseRateSelectionRepository {
        private final Map<UUID, BaseRateSelection> selections = new ConcurrentHashMap<>();
        private final List<BasePricingGridVersion> gridVersions = new ArrayList<>();
        private final List<BasePricingGridRow> gridRows = new ArrayList<>();
        private final List<BaseRateSelectionAudit> audits = new ArrayList<>();

        @Override
        public void save(BaseRateSelection selection) {
            selections.put(selection.selectionId(), selection);
        }

        @Override
        public List<BasePricingGridVersion> findGridVersion(String tenantId, String productCode,
                String investorCode, String channelCode) {
            return gridVersions.stream()
                    .filter(v -> tenantId.equals(v.tenantId()))
                    .filter(v -> Objects.equals(productCode, v.productCode()))
                    .filter(v -> Objects.equals(investorCode, v.investorCode()))
                    .filter(v -> Objects.equals(channelCode, v.channelCode()))
                    .toList();
        }

        @Override
        public List<BasePricingGridRow> findGridRows(String tenantId, UUID gridVersionId, int lockPeriodDays) {
            return gridRows.stream()
                    .filter(row -> tenantId.equals(row.tenantId()))
                    .filter(row -> gridVersionId.equals(row.gridVersionId()))
                    .filter(row -> row.lockPeriodDays() == lockPeriodDays)
                    .toList();
        }

        @Override
        public void saveAudit(BaseRateSelectionAudit audit) {
            audits.add(audit);
        }

        public void addGridVersion(BasePricingGridVersion version) {
            gridVersions.add(version);
        }

        public void addGridRow(BasePricingGridRow row) {
            gridRows.add(row);
        }
    }

    // ── Exceptions ──

    public static class BaseRateSelectionValidationException extends RuntimeException {
        public BaseRateSelectionValidationException(String message) {
            super(message);
        }
    }

    public static class BaseRateSelectionAccessDeniedException extends RuntimeException {
        public BaseRateSelectionAccessDeniedException(String message) {
            super(message);
        }
    }

    public static class BaseRateSelectionNotFoundException extends RuntimeException {
        public BaseRateSelectionNotFoundException(String message) {
            super(message);
        }
    }

    public static class BaseRateSelectionConflictException extends RuntimeException {
        public BaseRateSelectionConflictException(String message) {
            super(message);
        }
    }

    public static class BaseRateSelectionGridNotFoundException extends RuntimeException {
        public BaseRateSelectionGridNotFoundException(String message) {
            super(message);
        }
    }

    public static class BaseRateSelectionLockPeriodUnsupportedException extends RuntimeException {
        public BaseRateSelectionLockPeriodUnsupportedException(String message) {
            super(message);
        }
    }

    public static class BaseRateSelectionNoteRateUnavailableException extends RuntimeException {
        public BaseRateSelectionNoteRateUnavailableException(String message) {
            super(message);
        }
    }
}
