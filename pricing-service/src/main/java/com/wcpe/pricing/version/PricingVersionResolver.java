package com.wcpe.pricing.version;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PricingVersionResolver {
    public static final String VERSION_GRAPH_RESOLVE_PERMISSION = "pricing.version-graph.resolve";
    public static final String VERSION_GRAPH_READ_PERMISSION = "pricing.version-graph.read";
    public static final String REPLAY_HISTORICAL_PERMISSION = "pricing.replay.historical";

    private final VersionGraphRepository repository;

    public PricingVersionResolver(VersionGraphRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public VersionGraphResult resolveVersionGraph(String tenantId, VersionGraphHeaders headers,
            ResolveVersionGraphRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, VERSION_GRAPH_RESOLVE_PERMISSION);
        validateResolveRequest(request);

        if (request.asOf().isAfter(Instant.now())
                && !headers.permissions().contains(REPLAY_HISTORICAL_PERMISSION)) {
            throw new VersionGraphAccessDeniedException("AS_OF_FUTURE_FORBIDDEN");
        }

        Map<ArtifactType, PinnedVersionRef> pinnedRefs = parsePinnedVersionRefs(request.pinnedVersionRefs());
        List<String> warnings = new ArrayList<>();
        List<VersionRef> resolvedRefs = new ArrayList<>();

        for (ArtifactType artifactType : request.requiredArtifacts()) {
            PinnedVersionRef pinned = pinnedRefs.get(artifactType);
            ArtifactVersion resolved = resolveArtifactVersion(tenantId, artifactType, request.productCode(),
                    request.investorCode(), request.channelCode(), request.asOf(), pinned);
            if (pinned != null) {
                warnings.add("PINNED_VERSION_USED");
            }
            resolvedRefs.add(new VersionRef(artifactType.name(), resolved.id(), resolved.versionHash()));
        }

        String graphHash = computeGraphHash(resolvedRefs);
        UUID graphId = UUID.randomUUID();
        VersionGraphResult result = new VersionGraphResult(graphId, request.asOf(), resolvedRefs, graphHash, warnings,
                request.productCode(), request.investorCode(), request.channelCode(), request.scenarioHash());
        repository.saveGraph(result);

        Instant occurredAt = Instant.now();
        repository.saveEvent(new VersionGraphEvent(UUID.randomUUID(), tenantId, graphId,
                "pricing.version-graph-resolved.v1", headers.actorId(), headers.correlationId(), headers.idempotencyKey(),
                occurredAt, Map.of(
                        "version_graph_id", graphId.toString(),
                        "graph_hash", graphHash,
                        "as_of", request.asOf().toString())));
        repository.saveAudit(new VersionGraphAudit(UUID.randomUUID(), tenantId, graphId, request.asOf(), graphHash,
                headers.actorId(), headers.correlationId(), request.pinnedVersionRefs(), request.scenarioHash(), occurredAt));

        return result;
    }

    private ArtifactVersion resolveArtifactVersion(String tenantId, ArtifactType artifactType, String productCode,
            String investorCode, String channelCode, Instant asOf, PinnedVersionRef pinned) {
        List<ArtifactVersion> versions = repository.findArtifactVersions(tenantId, artifactType, productCode,
                investorCode, channelCode);

        if (pinned != null) {
            return versions.stream()
                    .filter(version -> pinned.versionId().equals(version.id()))
                    .findFirst()
                    .orElseThrow(() -> new VersionGraphNotFoundException(
                            "VERSION_NOT_FOUND: pinned " + artifactType + " version not found"));
        }

        List<ArtifactVersion> effective = versions.stream()
                .filter(version -> version.status() == VersionArtifactStatus.PUBLISHED)
                .filter(version -> !version.effectiveFrom().isAfter(asOf))
                .filter(version -> version.effectiveTo() == null || asOf.isBefore(version.effectiveTo()))
                .toList();

        if (effective.isEmpty()) {
            throw new VersionGraphNotFoundException(
                    "VERSION_NOT_FOUND: no published " + artifactType + " version active at as-of");
        }
        if (effective.size() > 1) {
            for (int left = 0; left < effective.size(); left++) {
                for (int right = left + 1; right < effective.size(); right++) {
                    ArtifactVersion leftVersion = effective.get(left);
                    ArtifactVersion rightVersion = effective.get(right);
                    if (windowsOverlap(leftVersion.effectiveFrom(), leftVersion.effectiveTo(),
                            rightVersion.effectiveFrom(), rightVersion.effectiveTo())) {
                        throw new VersionGraphConflictException(
                                "VERSION_AMBIGUOUS: multiple overlapping " + artifactType + " versions");
                    }
                }
            }
        }

        return effective.stream()
                .max(Comparator.comparing(ArtifactVersion::effectiveFrom)
                        .thenComparing(ArtifactVersion::versionNumber))
                .orElseThrow();
    }

    private static Map<ArtifactType, PinnedVersionRef> parsePinnedVersionRefs(List<String> rawRefs) {
        Map<ArtifactType, PinnedVersionRef> pinnedRefs = new HashMap<>();
        for (String rawRef : rawRefs) {
            requireText(rawRef, "pinned_version_ref is required");
            String[] parts = rawRef.split("[:|,]", 3);
            if (parts.length < 2) {
                throw new VersionGraphValidationException("pinned_version_ref must include artifact_type and version_id");
            }
            try {
                ArtifactType artifactType = ArtifactType.valueOf(parts[0]);
                UUID versionId = UUID.fromString(parts[1]);
                String immutableHash = parts.length == 3 ? parts[2] : null;
                pinnedRefs.put(artifactType, new PinnedVersionRef(artifactType, versionId, immutableHash));
            } catch (IllegalArgumentException ex) {
                throw new VersionGraphValidationException("pinned_version_ref is invalid");
            }
        }
        return pinnedRefs;
    }

    private static void validateResolveRequest(ResolveVersionGraphRequest request) {
        if (request == null) {
            throw new VersionGraphValidationException("request is required");
        }
        requireText(request.productCode(), "product_code is required");
        if (request.asOf() == null) {
            throw new VersionGraphValidationException("as_of is required");
        }
        if (request.requiredArtifacts().isEmpty()) {
            throw new VersionGraphValidationException("required_artifacts is required");
        }
        if (request.requiredArtifacts().stream().anyMatch(Objects::isNull)) {
            throw new VersionGraphValidationException("required_artifact is required");
        }
    }

    private static boolean windowsOverlap(Instant leftFrom, Instant leftTo, Instant rightFrom, Instant rightTo) {
        Instant normalizedLeftTo = leftTo == null ? Instant.MAX : leftTo;
        Instant normalizedRightTo = rightTo == null ? Instant.MAX : rightTo;
        return leftFrom.isBefore(normalizedRightTo) && rightFrom.isBefore(normalizedLeftTo);
    }

    private static String computeGraphHash(List<VersionRef> versionRefs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<VersionRef> sortedRefs = versionRefs.stream()
                    .sorted(Comparator.comparing(VersionRef::artifactType)
                            .thenComparing(ref -> ref.versionId().toString()))
                    .toList();
            boolean first = true;
            for (VersionRef ref : sortedRefs) {
                if (!first) {
                    digest.update((byte) 0);
                }
                first = false;
                String refValue = ref.artifactType() + ":" + ref.versionId() + ":" + ref.versionHash();
                digest.update(refValue.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static void requirePermission(VersionGraphHeaders headers, String permission) {
        if (headers == null) {
            throw new VersionGraphAccessDeniedException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!VERSION_GRAPH_READ_PERMISSION.equals(permission)) {
            requireText(headers.idempotencyKey(), "idempotency_key is required");
        }
        if (!headers.permissions().contains(permission)) {
            throw new VersionGraphAccessDeniedException(permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new VersionGraphValidationException(message);
        }
    }

    public enum ArtifactType {
        GRID,
        ROUNDING,
        PAR_RATE,
        CAP_FLOOR,
        REASON_CODE,
        ADJUSTMENT
    }

    public record VersionRef(
            String artifactType,
            UUID versionId,
            String versionHash) {
    }

    public record VersionScope(
            String productCode,
            String investorCode,
            String channelCode) {
        public VersionScope {
            if (productCode == null || productCode.isBlank()) {
                throw new VersionGraphValidationException("product_code is required");
            }
        }
    }

    public record ResolveVersionGraphRequest(
            String productCode,
            String investorCode,
            String channelCode,
            Instant asOf,
            String scenarioHash,
            List<ArtifactType> requiredArtifacts,
            List<String> pinnedVersionRefs) {
        public ResolveVersionGraphRequest {
            requiredArtifacts = requiredArtifacts == null ? List.of() : List.copyOf(requiredArtifacts);
            pinnedVersionRefs = pinnedVersionRefs == null ? List.of() : List.copyOf(pinnedVersionRefs);
        }
    }

    public record VersionGraphResult(
            UUID versionGraphId,
            Instant asOf,
            List<VersionRef> versionRefs,
            String graphHash,
            List<String> warnings,
            String productCode,
            String investorCode,
            String channelCode,
            String scenarioHash) {
        public VersionGraphResult {
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record VersionGraphHeaders(
            Set<String> permissions,
            String actorId,
            String correlationId,
            String idempotencyKey) {
        public VersionGraphHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record ArtifactVersion(
            UUID id,
            String tenantId,
            String productCode,
            String investorCode,
            String channelCode,
            ArtifactType artifactType,
            int versionNumber,
            VersionArtifactStatus status,
            Instant effectiveFrom,
            Instant effectiveTo,
            String versionHash) {
        public ArtifactVersion {
            if (effectiveFrom == null) {
                throw new VersionGraphValidationException("effective_from is required");
            }
            if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
                throw new VersionGraphValidationException("effective_to must be after effective_from");
            }
        }
    }

    public record PinnedVersionRef(
            ArtifactType artifactType,
            UUID versionId,
            String immutableHash) {
    }

    public record VersionGraphAudit(
            UUID auditId,
            String tenantId,
            UUID graphId,
            Instant asOf,
            String graphHash,
            String actorId,
            String correlationId,
            List<String> pinnedRefs,
            String scenarioHash,
            Instant occurredAt) {
        public VersionGraphAudit {
            pinnedRefs = pinnedRefs == null ? List.of() : List.copyOf(pinnedRefs);
        }
    }

    public record VersionGraphEvent(
            UUID eventId,
            String tenantId,
            UUID graphId,
            String eventType,
            String actorId,
            String correlationId,
            String idempotencyKey,
            Instant occurredAt,
            Map<String, String> payload) {
        public VersionGraphEvent {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    public interface VersionGraphRepository {
        void saveGraph(VersionGraphResult result);

        Optional<VersionGraphResult> findById(String tenantId, UUID graphId);

        List<ArtifactVersion> findArtifactVersions(String tenantId, ArtifactType type, String productCode,
                String investorCode, String channelCode);

        void saveEvent(VersionGraphEvent event);

        void saveAudit(VersionGraphAudit audit);

        void invalidateCache(String tenantId, String cacheKey);

        List<String> findEventsByTenant(String tenantId);
    }

    public static final class InMemoryVersionGraphRepository implements VersionGraphRepository {
        private final Map<UUID, VersionGraphResult> graphs = new ConcurrentHashMap<>();
        private final List<ArtifactVersion> artifactVersions = Collections.synchronizedList(new ArrayList<>());
        private final List<VersionGraphEvent> events = Collections.synchronizedList(new ArrayList<>());
        private final List<VersionGraphAudit> audits = Collections.synchronizedList(new ArrayList<>());
        private final Set<String> invalidatedCacheKeys = ConcurrentHashMap.newKeySet();

        @Override
        public void saveGraph(VersionGraphResult result) {
            graphs.put(result.versionGraphId(), result);
        }

        @Override
        public Optional<VersionGraphResult> findById(String tenantId, UUID graphId) {
            return Optional.ofNullable(graphs.get(graphId));
        }

        @Override
        public List<ArtifactVersion> findArtifactVersions(String tenantId, ArtifactType type, String productCode,
                String investorCode, String channelCode) {
            synchronized (artifactVersions) {
                return artifactVersions.stream()
                        .filter(version -> tenantId.equals(version.tenantId()))
                        .filter(version -> type == version.artifactType())
                        .filter(version -> Objects.equals(productCode, version.productCode()))
                        .filter(version -> Objects.equals(investorCode, version.investorCode()))
                        .filter(version -> Objects.equals(channelCode, version.channelCode()))
                        .toList();
            }
        }

        @Override
        public void saveEvent(VersionGraphEvent event) {
            events.add(event);
        }

        @Override
        public void saveAudit(VersionGraphAudit audit) {
            audits.add(audit);
        }

        @Override
        public void invalidateCache(String tenantId, String cacheKey) {
            invalidatedCacheKeys.add(cacheKey);
        }

        @Override
        public List<String> findEventsByTenant(String tenantId) {
            synchronized (events) {
                return events.stream()
                        .filter(event -> tenantId.equals(event.tenantId()))
                        .map(VersionGraphEvent::eventType)
                        .toList();
            }
        }

        public List<VersionGraphEvent> events() {
            synchronized (events) {
                return List.copyOf(events);
            }
        }

        public List<VersionGraphAudit> audits() {
            synchronized (audits) {
                return List.copyOf(audits);
            }
        }

        public boolean wasCacheInvalidated(String key) {
            return invalidatedCacheKeys.contains(key);
        }

        public void addArtifactVersion(ArtifactVersion artifactVersion) {
            artifactVersions.add(artifactVersion);
        }
    }

    public static class VersionGraphValidationException extends RuntimeException {
        public VersionGraphValidationException(String message) {
            super(message);
        }
    }

    public static class VersionGraphAccessDeniedException extends RuntimeException {
        public VersionGraphAccessDeniedException(String message) {
            super(message);
        }
    }

    public static class VersionGraphNotFoundException extends RuntimeException {
        public VersionGraphNotFoundException(String message) {
            super(message);
        }
    }

    public static class VersionGraphConflictException extends RuntimeException {
        public VersionGraphConflictException(String message) {
            super(message);
        }
    }
}
