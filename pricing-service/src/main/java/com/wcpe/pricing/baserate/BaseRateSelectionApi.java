package com.wcpe.pricing.baserate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseRateSelectionApi {
    public static final String BASE_RATE_READ_PERMISSION = "pricing.baserate.read";
    public static final String BASE_RATE_WRITE_PERMISSION = "pricing.baserate.write";
    public static final String GRID_IMPORT_PERMISSION = "pricing.grid.import";
    public static final String GRID_PUBLISH_PERMISSION = "pricing.grid.publish";
    public static final String GRID_READ_PERMISSION = "pricing.grid.read";

    private final BaseRateSelectionRepository repository;

    public BaseRateSelectionApi(BaseRateSelectionRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public BaseGridImportResponse importBaseGrid(String tenantId, BaseRateSelectionHeaders headers,
            BaseGridImportRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, GRID_IMPORT_PERMISSION);
        validateImportRequest(tenantId, request);

        UUID importId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();

        BasePricingGridVersion version = new BasePricingGridVersion(
                versionId,
                tenantId,
                request.productCode(),
                request.investorCode(),
                request.channelCode(),
                repository.nextGridVersionNumber(tenantId, request.productCode(), request.investorCode(), request.channelCode()),
                GridVersionStatus.DRAFT,
                request.effectiveFrom(),
                request.effectiveTo(),
                request.sourceDigest(),
                null,
                null,
                now,
                now);
        repository.addGridVersion(version);

        for (BaseGridRowDraft row : request.rows()) {
            repository.addGridRow(new BasePricingGridRow(
                    UUID.randomUUID(),
                    tenantId,
                    versionId,
                    row.lockPeriodDays(),
                    row.noteRate(),
                    row.basePrice(),
                    row.bucketKey(),
                    rowHash(row),
                    now));
        }

        BasePricingGridImport gridImport = new BasePricingGridImport(importId, tenantId, versionId,
                request.sourceType(), request.sourceDigest(), GridImportStatus.DRAFT, headers.actorId(), now, now,
                List.of());
        repository.saveGridImport(gridImport);
        repository.saveGridEvent(new BaseGridEvent(UUID.randomUUID(), tenantId, versionId,
                "pricing.base-grid-imported.v1", headers.actorId(), headers.correlationId(), headers.idempotencyKey(), now,
                Map.of("rowCount", String.valueOf(request.rows().size()), "sourceDigest", request.sourceDigest())));

        return new BaseGridImportResponse(importId, versionId, GridImportStatus.DRAFT,
                request.rows().size(), List.of(), "audit:" + importId, headers.correlationId());
    }

    public BaseGridValidationResult validateBaseGrid(String tenantId, BaseRateSelectionHeaders headers, UUID versionId) {
        requireTenant(tenantId);
        requirePermission(headers, GRID_IMPORT_PERMISSION);
        BasePricingGridVersion version = requireGridVersion(tenantId, versionId);
        List<String> errors = validatePersistedGrid(version);
        GridImportStatus status = errors.isEmpty() ? GridImportStatus.VALIDATED : GridImportStatus.VALIDATION_FAILED;
        repository.markGridImportStatus(tenantId, versionId, status, errors);
        repository.saveGridEvent(new BaseGridEvent(UUID.randomUUID(), tenantId, versionId,
                "pricing.base-grid-validated.v1", headers.actorId(), headers.correlationId(), headers.idempotencyKey(),
                Instant.now(), Map.of("status", status.name(), "errorCount", String.valueOf(errors.size()))));
        return new BaseGridValidationResult(versionId, status, errors, "audit:" + versionId, headers.correlationId());
    }

    public BaseGridPublishResponse publishBaseGrid(String tenantId, BaseRateSelectionHeaders headers, UUID versionId) {
        requireTenant(tenantId);
        requirePermission(headers, GRID_PUBLISH_PERMISSION);
        BasePricingGridVersion version = requireGridVersion(tenantId, versionId);
        BasePricingGridImport gridImport = repository.findGridImport(tenantId, versionId)
                .orElseThrow(() -> new BaseRateSelectionValidationException("grid import is required before publish"));
        if (gridImport.status() != GridImportStatus.VALIDATED) {
            throw new BaseRateSelectionValidationException("GRID_NOT_APPROVED: validated grid import is required before publish");
        }
        if (Objects.equals(gridImport.createdBy(), headers.actorId())) {
            throw new BaseRateSelectionValidationException("GRID_NOT_APPROVED: importer cannot publish own grid");
        }

        List<BasePricingGridVersion> overlaps = repository.findGridVersion(tenantId, version.productCode(),
                version.investorCode(), version.channelCode()).stream()
                .filter(candidate -> candidate.status() == GridVersionStatus.PUBLISHED)
                .filter(candidate -> !candidate.id().equals(versionId))
                .filter(candidate -> windowsOverlap(version.effectiveFrom(), version.effectiveTo(),
                        candidate.effectiveFrom(), candidate.effectiveTo()))
                .toList();
        if (!overlaps.isEmpty()) {
            throw new BaseRateSelectionConflictException("GRID_EFFECTIVE_WINDOW_OVERLAP");
        }

        BasePricingGridVersion published = version.withPublication(headers.actorId(), Instant.now());
        repository.replaceGridVersion(published);
        repository.markGridImportStatus(tenantId, versionId, GridImportStatus.PUBLISHED, List.of());
        repository.invalidateGridCache(tenantId, versionId);
        repository.saveGridEvent(new BaseGridEvent(UUID.randomUUID(), tenantId, versionId,
                "pricing.base-grid-published.v1", headers.actorId(), headers.correlationId(), headers.idempotencyKey(),
                Instant.now(), Map.of("rowCount", String.valueOf(repository.findGridRows(tenantId, versionId).size()),
                        "sourceDigest", version.sourceDigest())));

        return new BaseGridPublishResponse(versionId, GridVersionStatus.PUBLISHED,
                repository.findGridRows(tenantId, versionId).size(), "audit:" + versionId, headers.correlationId());
    }

    public BaseGridLookupResponse lookupBaseGridRow(String tenantId, BaseRateSelectionHeaders headers,
            BaseGridLookupRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, GRID_READ_PERMISSION);
        validateLookupRequest(request);
        BasePricingGridVersion version = resolveGridVersion(tenantId, request.productCode(), request.investorCode(),
                request.channelCode(), request.asOf());
        String bucketHash = bucketHash(request.bucketKey());
        List<BasePricingGridRow> matches = repository.findGridRows(tenantId, version.id(), request.lockPeriodDays()).stream()
                .filter(row -> row.noteRate().compareTo(request.noteRate()) == 0)
                .filter(row -> bucketHash(row.bucketKey()).equals(bucketHash))
                .toList();
        if (matches.isEmpty()) {
            throw new BaseRateSelectionNotFoundException("GRID_LOOKUP_NOT_FOUND");
        }
        if (matches.size() > 1) {
            throw new BaseRateSelectionConflictException("GRID_LOOKUP_AMBIGUOUS");
        }
        BasePricingGridRow row = matches.get(0);
        return new BaseGridLookupResponse(version.id(), row.id(), row.lockPeriodDays(), row.noteRate(), row.basePrice(),
                row.rowHash(), bucketHash, headers.correlationId());
    }

    public BaseRateSelectionResponse selectRate(String tenantId, BaseRateSelectionHeaders headers, BaseRateSelectionRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, BASE_RATE_WRITE_PERMISSION);
        validateSelectionRequest(tenantId, request);

        String requestFingerprint = stableHash("base-rate-selection-command", tenantId, request.scenarioId(),
                request.scenarioHash(), request.productCode(), request.investorCode(), request.channelCode(),
                request.lockPeriodDays(), request.asOf(), scale(request.requestedNoteRate()), request.selectionPolicyId());
        Optional<BaseRateSelectionIdempotencyRecord> existing = repository.findIdempotencyRecord(tenantId,
                headers.idempotencyKey());
        if (existing.isPresent()) {
            BaseRateSelectionIdempotencyRecord record = existing.get();
            if (!record.requestFingerprint().equals(requestFingerprint)) {
                throw new BaseRateSelectionConflictException("IDEMPOTENCY_CONFLICT");
            }
            return record.response();
        }

        UUID gridVersionId = resolveGridVersionId(tenantId, request.productCode(), request.investorCode(),
                request.channelCode(), request.asOf());

        List<BasePricingGridRow> matchingRows = repository.findGridRows(tenantId, gridVersionId, request.lockPeriodDays());
        if (matchingRows.isEmpty()) {
            throw new BaseRateSelectionLockPeriodUnsupportedException("LOCK_PERIOD_UNSUPPORTED");
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

        String resultHash = stableHash("base-rate-selection-result", tenantId, request.scenarioHash(), gridVersionId,
                selected.noteRate(), selected.basePrice(), request.lockPeriodDays(), request.asOf(),
                request.selectionPolicyId(), rankedCandidates);
        UUID selectionId = UUID.nameUUIDFromBytes((tenantId + ":" + resultHash).getBytes(StandardCharsets.UTF_8));

        BaseRateSelection selection = new BaseRateSelection(
                selectionId,
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

        repository.saveGridEvent(new BaseGridEvent(UUID.randomUUID(), tenantId, gridVersionId,
                "pricing.base-rate-selected.v1", headers.actorId(), headers.correlationId(), headers.idempotencyKey(),
                Instant.now(), Map.of("selectionId", selection.selectionId().toString(), "resultHash", resultHash,
                        "scenarioHash", request.scenarioHash(), "lockPeriodDays", String.valueOf(request.lockPeriodDays()))));

        BaseRateSelectionResponse response = new BaseRateSelectionResponse(
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
        repository.saveIdempotencyRecord(new BaseRateSelectionIdempotencyRecord(tenantId, headers.idempotencyKey(),
                requestFingerprint, response, Instant.now()));
        return response;
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
            throw new BaseRateSelectionNoteRateUnavailableException("PRICE_ROW_MISSING_NOTE_RATE");
        }

        Optional<CandidateRate> exactMatch = candidates.stream()
                .filter(c -> c.noteRate().compareTo(requestedNoteRate) == 0)
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        throw new BaseRateSelectionNoteRateUnavailableException("PRICE_ROW_MISSING_NOTE_RATE");
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(5, BigDecimal.ROUND_HALF_UP);
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

    private static void validateImportRequest(String tenantId, BaseGridImportRequest request) {
        if (request == null) {
            throw new BaseRateSelectionValidationException("request is required");
        }
        requireText(request.productCode(), "product_code is required");
        requireText(request.investorCode(), "investor_code is required");
        requireText(request.channelCode(), "channel_code is required");
        requireText(request.sourceType(), "source_type is required");
        requireText(request.sourceDigest(), "source_digest is required");
        if (request.effectiveFrom() == null) {
            throw new BaseRateSelectionValidationException("effective_from is required");
        }
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new BaseRateSelectionValidationException("effective_to must be after effective_from");
        }
        if (!request.approvalWorkflowConfigured()) {
            throw new BaseRateSelectionValidationException("POLICY_NOT_SATISFIED: approval workflow configuration is required");
        }
        if (request.allowedLockPeriodDays().isEmpty()) {
            throw new BaseRateSelectionValidationException("POLICY_NOT_SATISFIED: lock period configuration is required");
        }
        if (request.rows().isEmpty()) {
            throw new BaseRateSelectionValidationException("at least one grid row is required");
        }
        Set<String> keys = new HashSet<>();
        for (BaseGridRowDraft row : request.rows()) {
            validateRowDraft(row, request.allowedLockPeriodDays(), request.allowedBucketDimensions());
            String key = row.lockPeriodDays() + "|" + row.noteRate().toPlainString() + "|" + bucketHash(row.bucketKey());
            if (!keys.add(key)) {
                throw new BaseRateSelectionValidationException("GRID_DUPLICATE_ROW");
            }
        }
    }

    private static void validateLookupRequest(BaseGridLookupRequest request) {
        if (request == null) {
            throw new BaseRateSelectionValidationException("request is required");
        }
        requireText(request.productCode(), "product_code is required");
        requireText(request.investorCode(), "investor_code is required");
        requireText(request.channelCode(), "channel_code is required");
        if (request.lockPeriodDays() <= 0) {
            throw new BaseRateSelectionValidationException("lock_period_days must be positive");
        }
        if (request.noteRate() == null) {
            throw new BaseRateSelectionValidationException("note_rate is required");
        }
        if (request.asOf() == null) {
            throw new BaseRateSelectionValidationException("as_of is required");
        }
    }

    private static void validateRowDraft(BaseGridRowDraft row, Set<Integer> allowedLockPeriodDays,
            Set<String> allowedBucketDimensions) {
        if (row == null) {
            throw new BaseRateSelectionValidationException("grid row is required");
        }
        if (!allowedLockPeriodDays.contains(row.lockPeriodDays())) {
            throw new BaseRateSelectionValidationException("GRID_LOCK_PERIOD_UNCONFIGURED");
        }
        if (row.noteRate() == null || row.basePrice() == null) {
            throw new BaseRateSelectionValidationException("GRID_SCALE_INVALID");
        }
        if (row.noteRate().scale() > 5 || row.basePrice().scale() > 5) {
            throw new BaseRateSelectionValidationException("GRID_SCALE_INVALID");
        }
        Set<String> bucketKeys = row.bucketKey() == null ? Set.of() : row.bucketKey().keySet();
        if (!allowedBucketDimensions.containsAll(bucketKeys)) {
            throw new BaseRateSelectionValidationException("GRID_BUCKET_DIMENSION_UNKNOWN");
        }
    }

    private List<String> validatePersistedGrid(BasePricingGridVersion version) {
        List<String> errors = new ArrayList<>();
        List<BasePricingGridRow> rows = repository.findGridRows(version.tenantId(), version.id());
        if (rows.isEmpty()) {
            errors.add("GRID_ROWS_REQUIRED");
        }
        Set<String> keys = new HashSet<>();
        for (BasePricingGridRow row : rows) {
            String key = row.lockPeriodDays() + "|" + row.noteRate().toPlainString() + "|" + bucketHash(row.bucketKey());
            if (!keys.add(key)) {
                errors.add("GRID_DUPLICATE_ROW");
            }
        }
        return List.copyOf(errors);
    }

    private BasePricingGridVersion requireGridVersion(String tenantId, UUID versionId) {
        if (versionId == null) {
            throw new BaseRateSelectionValidationException("version_id is required");
        }
        return repository.findGridVersionById(tenantId, versionId)
                .orElseThrow(() -> new BaseRateSelectionGridNotFoundException("grid version not found"));
    }

    private static boolean windowsOverlap(Instant leftFrom, Instant leftTo, Instant rightFrom, Instant rightTo) {
        Instant normalizedLeftTo = leftTo == null ? Instant.MAX : leftTo;
        Instant normalizedRightTo = rightTo == null ? Instant.MAX : rightTo;
        return leftFrom.isBefore(normalizedRightTo) && rightFrom.isBefore(normalizedLeftTo);
    }

    private static String rowHash(BaseGridRowDraft row) {
        return row.lockPeriodDays() + ":" + row.noteRate().setScale(5, BigDecimal.ROUND_HALF_UP).toPlainString()
                + ":" + row.basePrice().setScale(5, BigDecimal.ROUND_HALF_UP).toPlainString() + ":"
                + bucketHash(row.bucketKey());
    }

    private static String bucketHash(Map<String, String> bucketKey) {
        if (bucketKey == null || bucketKey.isEmpty()) {
            return "default";
        }
        TreeMap<String, String> sorted = new TreeMap<>(bucketKey);
        List<String> parts = new ArrayList<>();
        sorted.forEach((key, value) -> parts.add(key + "=" + value));
        return String.join("|", parts);
    }

    private static void requirePermission(BaseRateSelectionHeaders headers, String permission) {
        if (headers == null) {
            throw new BaseRateSelectionAccessDeniedException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!GRID_READ_PERMISSION.equals(permission)) {
            requireText(headers.idempotencyKey(), "idempotency_key is required");
        }
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

    public enum GridImportStatus {
        DRAFT,
        VALIDATED,
        VALIDATION_FAILED,
        PUBLISHED
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
            value = value.setScale(5, BigDecimal.ROUND_HALF_UP);
        }
    }

    public record Price(BigDecimal value) {
        public Price {
            if (value == null) {
                throw new BaseRateSelectionValidationException("price must not be null");
            }
            value = value.setScale(5, BigDecimal.ROUND_HALF_UP);
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
        public BasePricingGridVersion withPublication(String actorId, Instant publishedAt) {
            return new BasePricingGridVersion(id, tenantId, productCode, investorCode, channelCode, versionNumber,
                    GridVersionStatus.PUBLISHED, effectiveFrom, effectiveTo, sourceDigest, actorId, publishedAt, createdAt,
                    publishedAt);
        }
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

    public record BasePricingGridImport(
            UUID importId,
            String tenantId,
            UUID gridVersionId,
            String sourceType,
            String sourceDigest,
            GridImportStatus status,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            List<String> validationMessages) {
        public BasePricingGridImport {
            validationMessages = validationMessages == null ? List.of() : List.copyOf(validationMessages);
        }

        public BasePricingGridImport withStatus(GridImportStatus status, List<String> validationMessages) {
            return new BasePricingGridImport(importId, tenantId, gridVersionId, sourceType, sourceDigest, status,
                    createdBy, createdAt, Instant.now(), validationMessages);
        }
    }

    public record BaseGridEvent(
            UUID eventId,
            String tenantId,
            UUID gridVersionId,
            String eventType,
            String actorId,
            String correlationId,
            String idempotencyKey,
            Instant occurredAt,
            Map<String, String> payload) {
        public BaseGridEvent {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    public record BaseGridRowDraft(
            int lockPeriodDays,
            BigDecimal noteRate,
            BigDecimal basePrice,
            Map<String, String> bucketKey) {
        public BaseGridRowDraft {
            bucketKey = bucketKey == null ? Map.of() : Map.copyOf(bucketKey);
        }
    }

    public record BaseGridImportRequest(
            String productCode,
            String investorCode,
            String channelCode,
            Instant effectiveFrom,
            Instant effectiveTo,
            String sourceType,
            String sourceDigest,
            boolean approvalWorkflowConfigured,
            Set<Integer> allowedLockPeriodDays,
            Set<String> allowedBucketDimensions,
            List<BaseGridRowDraft> rows) {
        public BaseGridImportRequest {
            allowedLockPeriodDays = allowedLockPeriodDays == null ? Set.of() : Set.copyOf(allowedLockPeriodDays);
            allowedBucketDimensions = allowedBucketDimensions == null ? Set.of() : Set.copyOf(allowedBucketDimensions);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record BaseGridImportResponse(
            UUID importId,
            UUID gridVersionId,
            GridImportStatus status,
            int rowCount,
            List<String> validationMessages,
            String auditRef,
            String correlationId) {
        public BaseGridImportResponse {
            validationMessages = validationMessages == null ? List.of() : List.copyOf(validationMessages);
        }
    }

    public record BaseGridValidationResult(
            UUID gridVersionId,
            GridImportStatus status,
            List<String> validationMessages,
            String auditRef,
            String correlationId) {
        public BaseGridValidationResult {
            validationMessages = validationMessages == null ? List.of() : List.copyOf(validationMessages);
        }
    }

    public record BaseGridPublishResponse(
            UUID gridVersionId,
            GridVersionStatus status,
            int rowCount,
            String auditRef,
            String correlationId) {
    }

    public record BaseGridLookupRequest(
            String productCode,
            String investorCode,
            String channelCode,
            int lockPeriodDays,
            BigDecimal noteRate,
            Instant asOf,
            Map<String, String> bucketKey) {
        public BaseGridLookupRequest {
            bucketKey = bucketKey == null ? Map.of() : Map.copyOf(bucketKey);
        }
    }

    public record BaseGridLookupResponse(
            UUID gridVersionId,
            UUID rowId,
            int lockPeriodDays,
            BigDecimal noteRate,
            BigDecimal basePrice,
            String rowHash,
            String bucketKeyHash,
            String correlationId) {
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

    public record BaseRateSelectionIdempotencyRecord(
            String tenantId,
            String idempotencyKey,
            String requestFingerprint,
            BaseRateSelectionResponse response,
            Instant createdAt) {
    }

    // ── Repository ──

    public interface BaseRateSelectionRepository {
        void save(BaseRateSelection selection);

        List<BasePricingGridVersion> findGridVersion(String tenantId, String productCode, String investorCode, String channelCode);

        List<BasePricingGridRow> findGridRows(String tenantId, UUID gridVersionId, int lockPeriodDays);

        List<BasePricingGridRow> findGridRows(String tenantId, UUID gridVersionId);

        Optional<BasePricingGridVersion> findGridVersionById(String tenantId, UUID gridVersionId);

        int nextGridVersionNumber(String tenantId, String productCode, String investorCode, String channelCode);

        void addGridVersion(BasePricingGridVersion version);

        void addGridRow(BasePricingGridRow row);

        void replaceGridVersion(BasePricingGridVersion version);

        void saveGridImport(BasePricingGridImport gridImport);

        Optional<BasePricingGridImport> findGridImport(String tenantId, UUID gridVersionId);

        void markGridImportStatus(String tenantId, UUID gridVersionId, GridImportStatus status,
                List<String> validationMessages);

        void saveGridEvent(BaseGridEvent event);

        void invalidateGridCache(String tenantId, UUID gridVersionId);

        void saveAudit(BaseRateSelectionAudit audit);

        Optional<BaseRateSelectionIdempotencyRecord> findIdempotencyRecord(String tenantId, String idempotencyKey);

        void saveIdempotencyRecord(BaseRateSelectionIdempotencyRecord record);
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
