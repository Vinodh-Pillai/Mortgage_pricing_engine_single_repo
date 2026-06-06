package com.wcpe.pricing.replay;

import java.math.BigDecimal;
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
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PricingReplayApi {
    public static final String PRICING_REPLAY_RUN_PERMISSION = "pricing.replay.run";
    public static final String PRICING_REPLAY_READ_PERMISSION = "pricing.replay.read";

    private final PricingReplayRepository repository;

    public PricingReplayApi(PricingReplayRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public PricingReplayResponse replay(String tenantId, PricingReplayHeaders headers, PricingReplayRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, PRICING_REPLAY_RUN_PERMISSION);
        validateRequest(request);

        PricingCalculationSnapshot snapshot = repository.findSnapshot(tenantId, request.sourceType(), request.sourceId())
                .orElseThrow(() -> new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND,
                        "pricing calculation snapshot was not found"));
        if (!tenantId.equals(snapshot.tenantId())) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND,
                    "pricing calculation snapshot tenant does not match request tenant");
        }
        if (!snapshot.schemaSupported()) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_SCHEMA_UNSUPPORTED,
                    "pricing replay snapshot schema is not supported");
        }
        if (snapshot.versionGraphHash() == null || snapshot.versionGraphHash().isBlank()) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_VERSION_UNAVAILABLE,
                    "immutable pricing version graph is required for replay");
        }

        Instant startedAt = Instant.now();
        ReplayedCalculation replayed = reconstructFromSnapshot(snapshot);
        List<PricingReplayDiff> diffs = classifyDiffs(tenantId, replayed, snapshot);
        PricingReplayStatus status = diffs.isEmpty() ? PricingReplayStatus.MATCHED : PricingReplayStatus.MISMATCHED;
        ReplayMismatchClass mismatchClass = diffs.isEmpty() ? null : ReplayMismatchClassifier.classify(diffs);
        if (request.expectedResultHash() != null && !request.expectedResultHash().isBlank()
                && !request.expectedResultHash().equals(replayed.resultHash())) {
            mismatchClass = ReplayMismatchClass.RESULT_HASH_MISMATCH;
            status = PricingReplayStatus.MISMATCHED;
            diffs = new ArrayList<>(diffs);
            diffs.add(new PricingReplayDiff(tenantId, null, "expectedResultHash", request.expectedResultHash(),
                    replayed.resultHash(), ReplayMismatchClass.RESULT_HASH_MISMATCH, DiffSeverity.ERROR));
        }

        UUID replayRunId = UUID.nameUUIDFromBytes((tenantId + ":" + request.sourceType() + ":" + request.sourceId()
                + ":" + replayed.resultHash() + ":" + headers.idempotencyKey()).getBytes(StandardCharsets.UTF_8));
        String evidenceRef = "pricing-replay:" + replayRunId;
        Instant completedAt = Instant.now();
        PricingReplayRun run = new PricingReplayRun(replayRunId, tenantId, request.sourceType(), request.sourceId(),
                request.mode(), status, snapshot.resultHash(), replayed.resultHash(), replayed.ledgerHash(), mismatchClass,
                evidenceRef, headers.actorId(), headers.correlationId(), startedAt, completedAt);
        repository.saveRun(run);
        for (PricingReplayDiff diff : diffs) {
            repository.saveDiff(diff.withReplayRunId(replayRunId));
        }
        repository.saveEvent(new PricingReplayEvent(status == PricingReplayStatus.MATCHED
                ? "pricing.replay-completed.v1" : "pricing.replay-mismatch-detected.v1", tenantId + ":" + replayRunId,
                tenantId, replayRunId, headers.actorId(), headers.correlationId(), headers.idempotencyKey(), completedAt,
                Map.of("sourceType", request.sourceType().name(), "sourceId", request.sourceId(),
                        "status", status.name(), "evidenceRef", evidenceRef)));
        repository.saveAudit(new PricingReplayAudit("BASE_PRICING_REPLAY_COMPLETED", tenantId, replayRunId,
                headers.actorId(), headers.correlationId(), request.sourceType(), request.sourceId(),
                snapshot.versionGraphHash(), replayed.resultHash(), evidenceRef));

        return new PricingReplayResponse(replayRunId, status, snapshot.resultHash(), replayed.resultHash(),
                replayed.ledgerHash(), mismatchClass, List.copyOf(diffs), evidenceRef, startedAt, completedAt);
    }

    public PricingReplayResponse get(String tenantId, UUID replayRunId, PricingReplayHeaders headers) {
        requireTenant(tenantId);
        requirePermission(headers, PRICING_REPLAY_READ_PERMISSION);
        if (replayRunId == null) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND, "replay_run_id is required");
        }
        PricingReplayRun run = repository.findRun(tenantId, replayRunId)
                .orElseThrow(() -> new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND,
                        "pricing replay run was not found"));
        return new PricingReplayResponse(run.id(), run.status(), run.originalHash(), run.replayHash(), run.ledgerHash(),
                run.mismatchClass(), repository.findDiffs(tenantId, replayRunId), run.evidenceRef(), run.startedAt(),
                run.completedAt());
    }

    private static ReplayedCalculation reconstructFromSnapshot(PricingCalculationSnapshot snapshot) {
        String ledgerHash = stableHash("pricing-replay-ledger", snapshot.ledgerEntries());
        String resultHash = stableHash("pricing-replay-result", snapshot.tenantId(), snapshot.sourceId(),
                snapshot.sourceType(), snapshot.scenarioHash(), snapshot.versionGraphHash(), snapshot.selectedRowHash(),
                snapshot.roundingPolicyRef(), snapshot.canonicalInput(), snapshot.resultValues(), ledgerHash);
        return new ReplayedCalculation(resultHash, ledgerHash, snapshot.versionGraphHash(), snapshot.selectedRowHash(),
                snapshot.roundingPolicyRef(), snapshot.resultValues(), snapshot.ledgerEntries());
    }

    private static List<PricingReplayDiff> classifyDiffs(String tenantId, ReplayedCalculation replayed,
            PricingCalculationSnapshot snapshot) {
        List<PricingReplayDiff> diffs = new ArrayList<>();
        addDiff(tenantId, diffs, "resultHash", snapshot.resultHash(), replayed.resultHash(),
                ReplayMismatchClass.RESULT_HASH_MISMATCH);
        addDiff(tenantId, diffs, "ledgerHash", snapshot.ledgerHash(), replayed.ledgerHash(),
                ReplayMismatchClass.LEDGER_HASH_MISMATCH);
        addDiff(tenantId, diffs, "versionGraphHash", snapshot.expectedVersionGraphHash(), replayed.versionGraphHash(),
                ReplayMismatchClass.VERSION_GRAPH_MISMATCH);
        addDiff(tenantId, diffs, "selectedRowHash", snapshot.expectedSelectedRowHash(), replayed.selectedRowHash(),
                ReplayMismatchClass.SELECTED_ROW_MISMATCH);
        addDiff(tenantId, diffs, "roundingPolicyRef", snapshot.expectedRoundingPolicyRef(), replayed.roundingPolicyRef(),
                ReplayMismatchClass.ROUNDING_POLICY_MISMATCH);
        for (Map.Entry<String, String> expected : snapshot.expectedResultValues().entrySet()) {
            String actual = replayed.resultValues().get(expected.getKey());
            if (!Objects.equals(expected.getValue(), actual)) {
                ReplayMismatchClass classification = isScaleOnlyDrift(expected.getValue(), actual)
                        ? ReplayMismatchClass.SCALE_ONLY_DRIFT : ReplayMismatchClass.RESULT_VALUE_MISMATCH;
                diffs.add(new PricingReplayDiff(tenantId, null, "resultValues." + expected.getKey(),
                        expected.getValue(), actual, classification, DiffSeverity.ERROR));
            }
        }
        return List.copyOf(diffs);
    }

    private static void addDiff(String tenantId, List<PricingReplayDiff> diffs, String path, String expected,
            String actual, ReplayMismatchClass classification) {
        if (expected != null && !Objects.equals(expected, actual)) {
            diffs.add(new PricingReplayDiff(tenantId, null, path, expected, actual, classification, DiffSeverity.ERROR));
        }
    }

    private static boolean isScaleOnlyDrift(String left, String right) {
        if (left == null || right == null || Objects.equals(left, right)) {
            return false;
        }
        try {
            return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static void validateRequest(PricingReplayRequest request) {
        if (request == null) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND, "request is required");
        }
        if (request.sourceType() == null) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND, "source_type is required");
        }
        requireText(request.sourceId(), "source_id is required");
        if (request.mode() == null) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND, "mode is required");
        }
    }

    private static void requirePermission(PricingReplayHeaders headers, String permission) {
        if (headers == null) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_FORBIDDEN, "headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (PRICING_REPLAY_RUN_PERMISSION.equals(permission)) {
            requireText(headers.idempotencyKey(), "idempotency_key is required");
        }
        if (!headers.permissions().contains(permission)) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_FORBIDDEN,
                    permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND, message);
        }
    }

    private static String stableHash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(canonical(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String canonical(Object value) {
        if (value instanceof BigDecimal number) {
            return number.toPlainString();
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, String> sorted = new TreeMap<>();
            map.forEach((key, mapValue) -> sorted.put(String.valueOf(key), canonical(mapValue)));
            return sorted.toString();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(PricingReplayApi::canonical).toList().toString();
        }
        return String.valueOf(value);
    }

    public enum ReplaySourceType {
        QUOTE,
        FINAL_PRICE,
        BASE_RATE_SELECTION
    }

    public enum ReplayMode {
        VERIFY,
        DIFF,
        GOLDEN_FIXTURE
    }

    public enum PricingReplayStatus {
        QUEUED,
        RUNNING,
        MATCHED,
        MISMATCHED,
        FAILED
    }

    public enum ReplayMismatchClass {
        RESULT_HASH_MISMATCH,
        LEDGER_HASH_MISMATCH,
        VERSION_GRAPH_MISMATCH,
        SELECTED_ROW_MISMATCH,
        ROUNDING_POLICY_MISMATCH,
        SCALE_ONLY_DRIFT,
        RESULT_VALUE_MISMATCH
    }

    public enum DiffSeverity {
        INFO,
        WARN,
        ERROR
    }

    public enum PricingReplayErrorCode {
        REPLAY_SOURCE_NOT_FOUND,
        REPLAY_VERSION_UNAVAILABLE,
        REPLAY_SCHEMA_UNSUPPORTED,
        REPLAY_HASH_MISMATCH,
        REPLAY_FORBIDDEN
    }

    public record PricingReplayHeaders(Set<String> permissions, String actorId, String correlationId,
            String idempotencyKey) {
        public PricingReplayHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record PricingReplayRequest(ReplaySourceType sourceType, String sourceId, String expectedResultHash,
            ReplayMode mode, String fixtureName) {
    }

    public record PricingReplayResponse(UUID replayRunId, PricingReplayStatus status, String originalHash,
            String replayHash, String ledgerHash, ReplayMismatchClass mismatchClass, List<PricingReplayDiff> diff,
            String evidenceArtifactRef, Instant startedAt, Instant completedAt) {
        public PricingReplayResponse {
            diff = diff == null ? List.of() : List.copyOf(diff);
        }
    }

    public record PricingCalculationSnapshot(String tenantId, ReplaySourceType sourceType, String sourceId,
            String scenarioHash, String versionGraphHash, String expectedVersionGraphHash, String selectedRowHash,
            String expectedSelectedRowHash, String roundingPolicyRef, String expectedRoundingPolicyRef,
            Map<String, String> canonicalInput, Map<String, String> resultValues, Map<String, String> expectedResultValues,
            List<PricingLedgerEntry> ledgerEntries, String resultHash, String ledgerHash, boolean schemaSupported) {
        public PricingCalculationSnapshot {
            requireText(tenantId, "snapshot tenant_id is required");
            if (sourceType == null) {
                throw new PricingReplayException(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND,
                        "snapshot source_type is required");
            }
            requireText(sourceId, "snapshot source_id is required");
            requireText(resultHash, "snapshot result_hash is required");
            requireText(ledgerHash, "snapshot ledger_hash is required");
            canonicalInput = canonicalInput == null ? Map.of() : Map.copyOf(canonicalInput);
            resultValues = resultValues == null ? Map.of() : Map.copyOf(resultValues);
            expectedResultValues = expectedResultValues == null ? resultValues : Map.copyOf(expectedResultValues);
            ledgerEntries = ledgerEntries == null ? List.of() : List.copyOf(ledgerEntries);
        }
    }

    public record PricingLedgerEntry(int ordinal, String step, String inputValue, String operation, String outputValue,
            String configRef, String reasonCode) {
    }

    public record PricingReplayRun(UUID id, String tenantId, ReplaySourceType sourceType, String sourceId,
            ReplayMode mode, PricingReplayStatus status, String originalHash, String replayHash, String ledgerHash,
            ReplayMismatchClass mismatchClass, String evidenceRef, String actorId, String correlationId, Instant startedAt,
            Instant completedAt) {
    }

    public record PricingReplayDiff(String tenantId, UUID replayRunId, String path, String originalValueRedacted,
            String replayValueRedacted, ReplayMismatchClass classification, DiffSeverity severity) {
        PricingReplayDiff withReplayRunId(UUID replayRunId) {
            return new PricingReplayDiff(tenantId, replayRunId, path, originalValueRedacted, replayValueRedacted,
                    classification, severity);
        }
    }

    public record PricingReplayEvent(String eventType, String eventKey, String tenantId, UUID replayRunId,
            String actorId, String correlationId, String idempotencyKey, Instant occurredAt, Map<String, String> payload) {
        public PricingReplayEvent {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    public record PricingReplayAudit(String action, String tenantId, UUID replayRunId, String actorId,
            String correlationId, ReplaySourceType sourceType, String sourceId, String versionGraphHash, String replayHash,
            String evidenceRef) {
    }

    private record ReplayedCalculation(String resultHash, String ledgerHash, String versionGraphHash,
            String selectedRowHash, String roundingPolicyRef, Map<String, String> resultValues,
            List<PricingLedgerEntry> ledgerEntries) {
        private ReplayedCalculation {
            resultValues = resultValues == null ? Map.of() : Map.copyOf(resultValues);
            ledgerEntries = ledgerEntries == null ? List.of() : List.copyOf(ledgerEntries);
        }
    }

    public static final class ReplayMismatchClassifier {
        private ReplayMismatchClassifier() {
        }

        public static ReplayMismatchClass classify(List<PricingReplayDiff> diffs) {
            if (diffs.stream().anyMatch(diff -> diff.classification() == ReplayMismatchClass.VERSION_GRAPH_MISMATCH)) {
                return ReplayMismatchClass.VERSION_GRAPH_MISMATCH;
            }
            List<PricingReplayDiff> materialDiffs = diffs.stream()
                    .filter(diff -> !"resultHash".equals(diff.path()))
                    .toList();
            if (!materialDiffs.isEmpty()
                    && materialDiffs.stream().allMatch(diff -> diff.classification() == ReplayMismatchClass.SCALE_ONLY_DRIFT)) {
                return ReplayMismatchClass.SCALE_ONLY_DRIFT;
            }
            if (diffs.stream().allMatch(diff -> diff.classification() == ReplayMismatchClass.SCALE_ONLY_DRIFT)) {
                return ReplayMismatchClass.SCALE_ONLY_DRIFT;
            }
            return diffs.stream()
                    .map(PricingReplayDiff::classification)
                    .findFirst()
                    .orElse(ReplayMismatchClass.RESULT_HASH_MISMATCH);
        }
    }

    public interface PricingReplayRepository {
        Optional<PricingCalculationSnapshot> findSnapshot(String tenantId, ReplaySourceType sourceType, String sourceId);

        void saveRun(PricingReplayRun run);

        Optional<PricingReplayRun> findRun(String tenantId, UUID replayRunId);

        void saveDiff(PricingReplayDiff diff);

        List<PricingReplayDiff> findDiffs(String tenantId, UUID replayRunId);

        void saveEvent(PricingReplayEvent event);

        void saveAudit(PricingReplayAudit audit);
    }

    public static final class InMemoryPricingReplayRepository implements PricingReplayRepository {
        private final Map<String, PricingCalculationSnapshot> snapshots = new ConcurrentHashMap<>();
        private final Map<UUID, PricingReplayRun> runs = new ConcurrentHashMap<>();
        private final List<PricingReplayDiff> diffs = new ArrayList<>();
        private final List<PricingReplayEvent> events = new ArrayList<>();
        private final List<PricingReplayAudit> audits = new ArrayList<>();

        public void saveSnapshot(PricingCalculationSnapshot snapshot) {
            snapshots.put(snapshotKey(snapshot.tenantId(), snapshot.sourceType(), snapshot.sourceId()), snapshot);
        }

        @Override
        public Optional<PricingCalculationSnapshot> findSnapshot(String tenantId, ReplaySourceType sourceType, String sourceId) {
            return Optional.ofNullable(snapshots.get(snapshotKey(tenantId, sourceType, sourceId)));
        }

        @Override
        public void saveRun(PricingReplayRun run) {
            runs.put(run.id(), run);
        }

        @Override
        public Optional<PricingReplayRun> findRun(String tenantId, UUID replayRunId) {
            PricingReplayRun run = runs.get(replayRunId);
            if (run == null || !tenantId.equals(run.tenantId())) {
                return Optional.empty();
            }
            return Optional.of(run);
        }

        @Override
        public void saveDiff(PricingReplayDiff diff) {
            diffs.add(diff);
        }

        @Override
        public List<PricingReplayDiff> findDiffs(String tenantId, UUID replayRunId) {
            return diffs.stream()
                    .filter(diff -> tenantId.equals(diff.tenantId()))
                    .filter(diff -> replayRunId.equals(diff.replayRunId()))
                    .sorted(Comparator.comparing(PricingReplayDiff::path))
                    .toList();
        }

        @Override
        public void saveEvent(PricingReplayEvent event) {
            events.add(event);
        }

        @Override
        public void saveAudit(PricingReplayAudit audit) {
            audits.add(audit);
        }

        public List<PricingReplayEvent> events() {
            return List.copyOf(events);
        }

        public List<PricingReplayAudit> audits() {
            return List.copyOf(audits);
        }

        public List<PricingReplayDiff> diffs() {
            return List.copyOf(diffs);
        }

        private static String snapshotKey(String tenantId, ReplaySourceType sourceType, String sourceId) {
            return tenantId + ":" + sourceType + ":" + sourceId;
        }
    }

    public static class PricingReplayException extends RuntimeException {
        private final PricingReplayErrorCode code;

        public PricingReplayException(PricingReplayErrorCode code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code);
        }

        public PricingReplayErrorCode code() {
            return code;
        }
    }
}
