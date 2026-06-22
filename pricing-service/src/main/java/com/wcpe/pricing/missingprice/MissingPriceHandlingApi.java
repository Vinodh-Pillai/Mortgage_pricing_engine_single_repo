package com.wcpe.pricing.missingprice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MissingPriceHandlingApi {
    public static final String MISSING_PRICE_WRITE_PERMISSION = "pricing.missing-price.write";
    public static final String MISSING_PRICE_READ_PERMISSION = "pricing.missing-price.read";
    public static final String MISSING_PRICE_RETRY_PERMISSION = "pricing.missing-price.retry";

    private final MissingPriceRepository repository;
    private final MissingPricePolicy policy;

    public MissingPriceHandlingApi(MissingPriceRepository repository) {
        this(repository, new MissingPricePolicy());
    }

    public MissingPriceHandlingApi(MissingPriceRepository repository, MissingPricePolicy policy) {
        this.repository = Objects.requireNonNull(repository);
        this.policy = Objects.requireNonNull(policy);
    }

    public MissingPriceHandlingResponse detectMissingPrice(String tenantId, MissingPriceHeaders headers,
            MissingPriceLookupRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, MISSING_PRICE_WRITE_PERMISSION);
        validateRequest(request);

        String fingerprint = stableHash("missing-price-command", tenantId, request);
        Optional<IdempotencyRecord> existing = repository.findIdempotencyRecord(tenantId, headers.idempotencyKey());
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.requestFingerprint().equals(fingerprint)) {
                throw new MissingPriceException(MissingPriceErrorCode.IDEMPOTENCY_CONFLICT,
                        "idempotency key was already used for a different missing-price request", 409, null);
            }
            return record.response();
        }

        Optional<MissingPriceFailure> failure = policy.classify(request, headers.canSeeDiagnostics());
        if (failure.isEmpty()) {
            MissingPriceHandlingResponse response = MissingPriceHandlingResponse.validPriceFound(headers.correlationId());
            repository.saveIdempotencyRecord(new IdempotencyRecord(tenantId, headers.idempotencyKey(), fingerprint,
                    response, Instant.now()));
            return response;
        }

        MissingPriceFailure missing = failure.get();
        UUID incidentId = UUID.nameUUIDFromBytes((tenantId + ":" + fingerprint).getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String diagnosticsRef = "missing-price-incident:" + incidentId;
        MissingPriceIncident incident = new MissingPriceIncident(
                incidentId,
                tenantId,
                request.scenarioHash(),
                request.productCode(),
                request.investorCode(),
                request.channelCode(),
                request.lockPeriodDays(),
                request.noteRate(),
                request.asOf(),
                missing.reason(),
                missing.diagnostic(),
                MissingPriceIncidentStatus.OPEN,
                headers.correlationId(),
                now,
                null,
                1);
        repository.saveIncident(incident);

        MissingPriceErrorResponse error = new MissingPriceErrorResponse(missing.reason().name(), missing.message(),
                missing.remediation(), diagnosticsRef, headers.correlationId(), missing.httpStatus());
        MissingPriceHandlingResponse response = new MissingPriceHandlingResponse(incidentId,
                MissingPriceIncidentStatus.OPEN, 1, "missing price incident created", error, diagnosticsRef,
                "replay:" + incidentId, headers.correlationId());
        repository.saveIdempotencyRecord(new IdempotencyRecord(tenantId, headers.idempotencyKey(), fingerprint,
                response, now));
        repository.saveOutboxEvent(new MissingPriceOutboxEvent(UUID.randomUUID(),
                "pricing.missing-price-detected.v1", tenantId + ":" + incidentId, tenantId, incidentId,
                headers.actorId(), headers.correlationId(), headers.idempotencyKey(), now,
                Map.of("reasonCode", missing.reason().name(), "scenarioHash", request.scenarioHash(),
                        "diagnosticsRef", diagnosticsRef)));
        repository.saveAudit(new MissingPriceAuditRecord(UUID.randomUUID(), "MISSING_PRICE_DETECTED", tenantId,
                incidentId, headers.actorId(), headers.correlationId(), List.of(request.gridVersionRef()),
                stableHash("audit", tenantId, incidentId, missing.reason(), request.redactedContext())));
        repository.putNegativeCache(negativeCacheKey(tenantId, request), incidentId);
        return response;
    }

    public MissingPriceIncident getIncident(String tenantId, UUID incidentId, MissingPriceHeaders headers) {
        requireTenant(tenantId);
        requirePermission(headers, MISSING_PRICE_READ_PERMISSION);
        if (incidentId == null) {
            throw new MissingPriceException(MissingPriceErrorCode.PRICE_GRID_MISSING, "incident_id is required", 400,
                    null);
        }
        return repository.findIncident(tenantId, incidentId)
                .orElseThrow(() -> new MissingPriceException(MissingPriceErrorCode.NOT_FOUND,
                        "missing price incident was not found", 404, null));
    }

    public MissingPriceRetryResponse retry(String tenantId, UUID incidentId, MissingPriceHeaders headers,
            MissingPriceRetryRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, MISSING_PRICE_RETRY_PERMISSION);
        if (incidentId == null) {
            throw new MissingPriceException(MissingPriceErrorCode.PRICE_GRID_MISSING, "incident_id is required", 400,
                    null);
        }
        if (request == null || request.lookupStatus() == null) {
            throw new MissingPriceException(MissingPriceErrorCode.PRICE_GRID_MISSING, "retry lookup_status is required",
                    400, null);
        }
        MissingPriceIncident incident = repository.findIncident(tenantId, incidentId)
                .orElseThrow(() -> new MissingPriceException(MissingPriceErrorCode.NOT_FOUND,
                        "missing price incident was not found", 404, null));

        Instant now = Instant.now();
        boolean resolved = request.lookupStatus() == MissingPriceLookupStatus.EXACT_MATCH;
        MissingPriceIncidentStatus retryStatus = resolved ? MissingPriceIncidentStatus.RESOLVED
                : MissingPriceIncidentStatus.RETRY_FAILED;
        MissingPriceRetry retry = new MissingPriceRetry(UUID.randomUUID(), incidentId, tenantId, headers.actorId(), now,
                retryStatus, request.resultRef(), resolved ? null : policy.reasonFor(request.lookupStatus()).name());
        repository.saveRetry(retry);
        MissingPriceIncident updated = incident.withStatus(resolved ? MissingPriceIncidentStatus.RESOLVED
                : MissingPriceIncidentStatus.OPEN, resolved ? now : null);
        repository.saveIncident(updated);
        repository.saveOutboxEvent(new MissingPriceOutboxEvent(UUID.randomUUID(), "pricing.missing-price-retried.v1",
                tenantId + ":" + incidentId, tenantId, incidentId, headers.actorId(), headers.correlationId(),
                headers.idempotencyKey(), now, Map.of("resultStatus", retryStatus.name())));
        repository.saveAudit(new MissingPriceAuditRecord(UUID.randomUUID(), "MISSING_PRICE_RETRY_RECORDED", tenantId,
                incidentId, headers.actorId(), headers.correlationId(), List.of(request.resultRef()),
                stableHash("retry", tenantId, incidentId, retryStatus, request.resultRef())));
        return new MissingPriceRetryResponse(retry.id(), incidentId, retryStatus, request.resultRef(),
                headers.correlationId());
    }

    public void handleGridPublished(String tenantId, String gridVersionRef) {
        requireTenant(tenantId);
        requireText(gridVersionRef, "grid_version_ref is required");
        repository.invalidateNegativeCache(tenantId, gridVersionRef);
    }

    private static void validateRequest(MissingPriceLookupRequest request) {
        if (request == null) {
            throw new MissingPriceException(MissingPriceErrorCode.PRICE_GRID_MISSING, "request is required", 400, null);
        }
        requireText(request.scenarioHash(), "scenario_hash is required");
        requireText(request.productCode(), "product_code is required");
        requireText(request.investorCode(), "investor_code is required");
        requireText(request.channelCode(), "channel_code is required");
        if (request.asOf() == null) {
            throw new MissingPriceException(MissingPriceErrorCode.PRICE_GRID_MISSING, "as_of is required", 400, null);
        }
        if (request.lookupStatus() == null) {
            throw new MissingPriceException(MissingPriceErrorCode.PRICE_GRID_MISSING, "lookup_status is required", 400,
                    null);
        }
    }

    private static void requirePermission(MissingPriceHeaders headers, String permission) {
        if (headers == null) {
            throw new MissingPriceException(MissingPriceErrorCode.TENANT_ACCESS_DENIED, "headers are required", 403,
                    null);
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!MISSING_PRICE_READ_PERMISSION.equals(permission)) {
            requireText(headers.idempotencyKey(), "idempotency_key is required");
        }
        if (!headers.permissions().contains(permission)) {
            throw new MissingPriceException(MissingPriceErrorCode.TENANT_ACCESS_DENIED,
                    permission + " permission is required", 403, null);
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new MissingPriceException(MissingPriceErrorCode.PRICE_GRID_MISSING, message, 400, null);
        }
    }

    private static String negativeCacheKey(String tenantId, MissingPriceLookupRequest request) {
        return "pricing:missing-price:%s:%s:%s:%s:%s:%s".formatted(tenantId, request.gridVersionRef(),
                request.scenarioHash(), request.lockPeriodDays(), request.noteRate(), request.bucketKeyHash());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(5, RoundingMode.HALF_UP);
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

    public static final class MissingPricePolicy {
        public Optional<MissingPriceFailure> classify(MissingPriceLookupRequest request, boolean includeDiagnosticRange) {
            MissingPriceReason reason = reasonFor(request.lookupStatus());
            if (reason == null) {
                return Optional.empty();
            }
            LookupDiagnostic diagnostic = LookupDiagnostic.from(request, reason, includeDiagnosticRange);
            return Optional.of(new MissingPriceFailure(reason, diagnostic, messageFor(reason), remediationFor(reason),
                    httpStatusFor(reason)));
        }

        MissingPriceReason reasonFor(MissingPriceLookupStatus status) {
            return switch (status) {
                case EXACT_MATCH -> null;
                case NO_ACTIVE_GRID -> MissingPriceReason.PRICE_GRID_MISSING;
                case MISSING_LOCK_PERIOD -> MissingPriceReason.PRICE_ROW_MISSING_LOCK_PERIOD;
                case MISSING_NOTE_RATE -> MissingPriceReason.PRICE_ROW_MISSING_NOTE_RATE;
                case MISSING_BUCKET -> MissingPriceReason.PRICE_ROW_MISSING_BUCKET;
                case GRID_SUSPENDED -> MissingPriceReason.PRICE_GRID_SUSPENDED;
                case AMBIGUOUS_ROWS -> MissingPriceReason.PRICE_LOOKUP_AMBIGUOUS;
                case VERSION_STALE -> MissingPriceReason.PRICE_VERSION_STALE;
            };
        }

        private static String messageFor(MissingPriceReason reason) {
            return switch (reason) {
                case PRICE_GRID_MISSING -> "no active pricing grid is available for the requested context";
                case PRICE_ROW_MISSING_LOCK_PERIOD -> "no grid row exists for the requested lock period";
                case PRICE_ROW_MISSING_NOTE_RATE -> "no grid row exists for the requested note rate";
                case PRICE_ROW_MISSING_BUCKET -> "no grid row exists for the requested bucket";
                case PRICE_GRID_SUSPENDED -> "the selected pricing grid version is suspended";
                case PRICE_LOOKUP_AMBIGUOUS -> "multiple pricing rows match the requested context";
                case PRICE_VERSION_STALE -> "the pinned pricing version is stale";
            };
        }

        private static String remediationFor(MissingPriceReason reason) {
            return switch (reason) {
                case PRICE_GRID_MISSING -> "publish an active grid or change the pricing as-of context";
                case PRICE_ROW_MISSING_LOCK_PERIOD -> "choose an available lock period or publish a row for this lock period";
                case PRICE_ROW_MISSING_NOTE_RATE -> "choose an available note rate in the authorized grid slice or publish the missing row";
                case PRICE_ROW_MISSING_BUCKET -> "review configured bucket dimensions for the scenario";
                case PRICE_GRID_SUSPENDED -> "reactivate or republish a valid grid version before retrying";
                case PRICE_LOOKUP_AMBIGUOUS -> "correct duplicate grid rows before pricing can continue";
                case PRICE_VERSION_STALE -> "retry against a current published grid version";
            };
        }

        private static int httpStatusFor(MissingPriceReason reason) {
            return switch (reason) {
                case PRICE_LOOKUP_AMBIGUOUS, PRICE_VERSION_STALE -> 409;
                default -> 422;
            };
        }
    }

    public enum MissingPriceLookupStatus {
        EXACT_MATCH,
        NO_ACTIVE_GRID,
        MISSING_LOCK_PERIOD,
        MISSING_NOTE_RATE,
        MISSING_BUCKET,
        GRID_SUSPENDED,
        AMBIGUOUS_ROWS,
        VERSION_STALE
    }

    public enum MissingPriceReason {
        PRICE_GRID_MISSING,
        PRICE_ROW_MISSING_LOCK_PERIOD,
        PRICE_ROW_MISSING_NOTE_RATE,
        PRICE_ROW_MISSING_BUCKET,
        PRICE_GRID_SUSPENDED,
        PRICE_LOOKUP_AMBIGUOUS,
        PRICE_VERSION_STALE
    }

    public enum MissingPriceIncidentStatus {
        OPEN,
        RESOLVED,
        RETRY_FAILED,
        VALID_PRICE_FOUND
    }

    public enum MissingPriceErrorCode {
        PRICE_GRID_MISSING,
        PRICE_ROW_MISSING_LOCK_PERIOD,
        PRICE_ROW_MISSING_NOTE_RATE,
        PRICE_ROW_MISSING_BUCKET,
        PRICE_GRID_SUSPENDED,
        PRICE_LOOKUP_AMBIGUOUS,
        PRICE_VERSION_STALE,
        TENANT_ACCESS_DENIED,
        IDEMPOTENCY_CONFLICT,
        NOT_FOUND
    }

    public record MissingPriceHeaders(Set<String> permissions, String actorId, String correlationId,
            String idempotencyKey) {
        public MissingPriceHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }

        boolean canSeeDiagnostics() {
            return permissions.contains(MISSING_PRICE_READ_PERMISSION);
        }
    }

    public record MissingPriceLookupRequest(
            String scenarioHash,
            String productCode,
            String investorCode,
            String channelCode,
            int lockPeriodDays,
            BigDecimal noteRate,
            Instant asOf,
            String gridVersionRef,
            String bucketKeyHash,
            MissingPriceLookupStatus lookupStatus,
            AvailablePriceRange authorizedRange,
            Map<String, String> redactedContext) {
        public MissingPriceLookupRequest {
            noteRate = scale(noteRate);
            gridVersionRef = gridVersionRef == null || gridVersionRef.isBlank() ? "current" : gridVersionRef;
            bucketKeyHash = bucketKeyHash == null || bucketKeyHash.isBlank() ? "default" : bucketKeyHash;
            redactedContext = redactedContext == null ? Map.of() : Map.copyOf(redactedContext);
        }
    }

    public record AvailablePriceRange(BigDecimal minNoteRate, BigDecimal maxNoteRate, BigDecimal minBasePrice,
            BigDecimal maxBasePrice, boolean sameTenantGridSlice) {
        public AvailablePriceRange {
            minNoteRate = scale(minNoteRate);
            maxNoteRate = scale(maxNoteRate);
            minBasePrice = scale(minBasePrice);
            maxBasePrice = scale(maxBasePrice);
        }
    }

    public record LookupDiagnostic(
            String scenarioHash,
            String productCode,
            String investorCode,
            String channelCode,
            int lockPeriodDays,
            BigDecimal noteRate,
            Instant asOf,
            String gridVersionRef,
            String bucketKeyHash,
            MissingPriceReason reasonCode,
            Map<String, String> redactedContext,
            Optional<AvailablePriceRange> authorizedRange) {
        static LookupDiagnostic from(MissingPriceLookupRequest request, MissingPriceReason reason,
                boolean includeDiagnosticRange) {
            Optional<AvailablePriceRange> range = includeDiagnosticRange
                    && request.authorizedRange() != null
                    && request.authorizedRange().sameTenantGridSlice()
                    ? Optional.of(request.authorizedRange())
                    : Optional.empty();
            return new LookupDiagnostic(request.scenarioHash(), request.productCode(), request.investorCode(),
                    request.channelCode(), request.lockPeriodDays(), request.noteRate(), request.asOf(),
                    request.gridVersionRef(), request.bucketKeyHash(), reason, request.redactedContext(), range);
        }

        public LookupDiagnostic {
            noteRate = scale(noteRate);
            redactedContext = redactedContext == null ? Map.of() : Map.copyOf(redactedContext);
            authorizedRange = authorizedRange == null ? Optional.empty() : authorizedRange;
        }
    }

    public record MissingPriceFailure(MissingPriceReason reason, LookupDiagnostic diagnostic, String message,
            String remediation, int httpStatus) {
    }

    public record MissingPriceIncident(
            UUID id,
            String tenantId,
            String scenarioHash,
            String productCode,
            String investorCode,
            String channelCode,
            int lockPeriodDays,
            BigDecimal noteRate,
            Instant asOf,
            MissingPriceReason reasonCode,
            LookupDiagnostic diagnostic,
            MissingPriceIncidentStatus status,
            String correlationId,
            Instant createdAt,
            Instant resolvedAt,
            int version) {
        public MissingPriceIncident {
            noteRate = scale(noteRate);
        }

        MissingPriceIncident withStatus(MissingPriceIncidentStatus status, Instant resolvedAt) {
            return new MissingPriceIncident(id, tenantId, scenarioHash, productCode, investorCode, channelCode,
                    lockPeriodDays, noteRate, asOf, reasonCode, diagnostic, status, correlationId, createdAt, resolvedAt,
                    version + 1);
        }
    }

    public record MissingPriceErrorResponse(String errorCode, String message, String remediation, String diagnosticsRef,
            String correlationId, int httpStatus) {
    }

    public record MissingPriceHandlingResponse(UUID id, MissingPriceIncidentStatus status, int version,
            String resultSummary, MissingPriceErrorResponse error, String auditRef, String replayRef, String correlationId) {
        static MissingPriceHandlingResponse validPriceFound(String correlationId) {
            return new MissingPriceHandlingResponse(null, MissingPriceIncidentStatus.VALID_PRICE_FOUND, 0,
                    "valid price row found; no missing-price incident created", null, null, null, correlationId);
        }
    }

    public record MissingPriceRetryRequest(MissingPriceLookupStatus lookupStatus, String resultRef) {
        public MissingPriceRetryRequest {
            resultRef = resultRef == null || resultRef.isBlank() ? "unresolved" : resultRef;
        }
    }

    public record MissingPriceRetry(UUID id, UUID incidentId, String tenantId, String attemptedBy, Instant attemptedAt,
            MissingPriceIncidentStatus resultStatus, String resultRef, String errorCode) {
    }

    public record MissingPriceRetryResponse(UUID retryId, UUID incidentId, MissingPriceIncidentStatus resultStatus,
            String resultRef, String correlationId) {
    }

    public record MissingPriceOutboxEvent(UUID eventId, String eventType, String eventKey, String tenantId,
            UUID incidentId, String actorId, String correlationId, String idempotencyKey, Instant occurredAt,
            Map<String, String> payload) {
        public MissingPriceOutboxEvent {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    public record MissingPriceAuditRecord(UUID auditId, String action, String tenantId, UUID incidentId,
            String actorId, String correlationId, List<String> versionRefs, String resultHash) {
        public MissingPriceAuditRecord {
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
        }
    }

    public record IdempotencyRecord(String tenantId, String idempotencyKey, String requestFingerprint,
            MissingPriceHandlingResponse response, Instant createdAt) {
    }

    public interface MissingPriceRepository {
        void saveIncident(MissingPriceIncident incident);

        Optional<MissingPriceIncident> findIncident(String tenantId, UUID incidentId);

        void saveRetry(MissingPriceRetry retry);

        List<MissingPriceRetry> findRetries(String tenantId, UUID incidentId);

        void saveOutboxEvent(MissingPriceOutboxEvent event);

        void saveAudit(MissingPriceAuditRecord audit);

        Optional<IdempotencyRecord> findIdempotencyRecord(String tenantId, String idempotencyKey);

        void saveIdempotencyRecord(IdempotencyRecord record);

        void putNegativeCache(String cacheKey, UUID incidentId);

        void invalidateNegativeCache(String tenantId, String gridVersionRef);
    }

    public static class MissingPriceException extends RuntimeException {
        private final MissingPriceErrorCode code;
        private final int httpStatus;
        private final String diagnosticsRef;

        public MissingPriceException(MissingPriceErrorCode code, String message, int httpStatus, String diagnosticsRef) {
            super(message);
            this.code = Objects.requireNonNull(code);
            this.httpStatus = httpStatus;
            this.diagnosticsRef = diagnosticsRef;
        }

        public MissingPriceErrorCode code() {
            return code;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public String diagnosticsRef() {
            return diagnosticsRef;
        }
    }
}
