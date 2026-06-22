package com.wcpe.margin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class MarginVersioningService {
  public static final String SENSITIVE_VERSION_PERMISSION = "pricing.margin.version.read_sensitive";
  public static final String MARGIN_VERSION_MANIFEST_API =
      "GET /api/v1/tenants/{tenantId}/margin-version-manifest";
  public static final String MARGIN_POLICY_VERSION_API =
      "GET /margin-policies/{policyId}/versions/{versionId}";
  public static final String INTERNAL_MARGIN_VERSION_RESOLUTION_API =
      "POST /internal/v1/margin-version-resolution";
  private static final String RESOLVER_ENGINE_VERSION = "margin-version-resolver-v1";

  public final AtomicInteger marginVersionResolveTotal = new AtomicInteger();
  public final AtomicInteger marginVersionCacheHitTotal = new AtomicInteger();
  public final AtomicInteger marginVersionOverlapDetectedTotal = new AtomicInteger();

  private final Clock clock;
  private final Store store;

  public MarginVersioningService(Clock clock) {
    this(clock, Store.failClosed("MarginVersioningService"));
  }

  MarginVersioningService(Clock clock, Store store) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.store = Objects.requireNonNull(store, "store is required");
  }

  public PublishReceipt publishPolicyVersion(String tenantId, String actorId, String correlationId,
      PolicyVersionRef version) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    Objects.requireNonNull(version, "version is required");
    if (!tenantId.equals(version.tenantId())) {
      throw new MarginVersioningException("TENANT_ACCESS_DENIED");
    }
    VersionKey key = new VersionKey(tenantId, version.policyType(), version.policyId(), version.versionId());
    PolicyVersionRef existing = store.publishedVersions().get(key);
    if (existing != null) {
      if (!existing.immutableHash().equals(version.immutableHash())) {
        throw new MarginVersioningException("VERSION_STALE");
      }
      return new PublishReceipt(version.policyId(), version.versionId(), List.of(), "audit:" + version.versionId());
    }
    rejectPublishedOverlap(version);
    store.publishedVersions().put(key, version);
    GovernanceChangePublishedEvent event = new GovernanceChangePublishedEvent(tenantId, version.policyType(),
        version.policyId(), version.versionId(), actorId, correlationId, Instant.now(clock));
    store.outbox().add(event);
    store.auditRecords().add(AuditRecord.completed(tenantId, version.policyId(), actorId, correlationId,
        "GOVERNANCE_CHANGE_PUBLISHED", clock));
    onGovernanceChangePublished(event);
    return new PublishReceipt(version.policyId(), version.versionId(), List.of(event), "audit:" + version.versionId());
  }

  public MarginCompVersionManifest resolveManifest(String tenantId, String scopeHash, Instant activeAtUtc,
      MarginResolutionScope quoteScope, List<String> requiredPolicyTypes) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    requireText(scopeHash, "scopeHash");
    Objects.requireNonNull(activeAtUtc, "activeAtUtc is required");
    Objects.requireNonNull(quoteScope, "quoteScope is required");
    List<String> requiredTypes = requiredTypes(requiredPolicyTypes);
    marginVersionResolveTotal.incrementAndGet();
    ManifestCacheKey cacheKey = new ManifestCacheKey(tenantId, scopeHash, activeAtBucket(activeAtUtc),
        publishedConfigHash(tenantId));
    MarginCompVersionManifest cached = store.derivedManifestCache().get(cacheKey);
    if (cached != null) {
      marginVersionCacheHitTotal.incrementAndGet();
      store.auditRecords().add(AuditRecord.completed(tenantId, cached.resultHash(), "system", cached.correlationId(),
          "MARGIN_VERSION_MANIFEST_CACHE_HIT", clock));
      return cached;
    }

    TreeMap<String, ManifestPolicyVersion> manifestVersions = new TreeMap<>();
    for (String policyType : requiredTypes) {
      PolicyVersionRef selected = selectActiveVersion(tenantId, policyType, activeAtUtc, quoteScope);
      manifestVersions.put(policyType, ManifestPolicyVersion.from(selected));
    }
    String configHash = stableHash(manifestVersions.values().stream().map(ManifestPolicyVersion::configHash).toList());
    String resultHash = stableHash(tenantId, scopeHash, activeAtUtc.toString(), RESOLVER_ENGINE_VERSION,
        canonicalManifestVersions(manifestVersions));
    MarginCompVersionManifest manifest = new MarginCompVersionManifest(tenantId, scopeHash, activeAtUtc,
        Map.copyOf(manifestVersions), configHash, RESOLVER_ENGINE_VERSION, resultHash, "replay:" + resultHash,
        "corr-margin-version-resolve");
    store.derivedManifestCache().put(cacheKey, manifest);
    store.replayManifests().put(manifest.replayRef(), manifest);
    store.outbox().add(new MarginCompVersionManifestResolvedEvent(tenantId, scopeHash, activeAtUtc, resultHash,
        RESOLVER_ENGINE_VERSION, Instant.now(clock)));
    store.auditRecords().add(AuditRecord.completed(tenantId, resultHash, "system", manifest.correlationId(),
        "MARGIN_COMP_VERSION_MANIFEST_RESOLVED", clock));
    return manifest;
  }

  public Optional<MarginCompVersionManifest> loadReplayManifest(String tenantId, String replayRef) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    requireText(replayRef, "replayRef");
    return Optional.ofNullable(store.replayManifests().get(replayRef))
        .filter(manifest -> tenantId.equals(manifest.tenantId()));
  }

  public Optional<PolicyVersionView> readPolicyVersion(String tenantId, String policyId, String versionId,
      String viewerPermission) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    requireText(policyId, "policyId");
    requireText(versionId, "versionId");
    return store.publishedVersions().values().stream()
        .filter(version -> tenantId.equals(version.tenantId()))
        .filter(version -> policyId.equals(version.policyId()))
        .filter(version -> versionId.equals(version.versionId()))
        .findFirst()
        .map(version -> PolicyVersionView.from(version, SENSITIVE_VERSION_PERMISSION.equals(viewerPermission)));
  }

  public MarginCompVersionManifest getMarginVersionManifest(String tenantId, MarginVersionManifestRequest request) {
    requireText(tenantId, "tenantId");
    Objects.requireNonNull(request, "request is required");
    return resolveManifest(tenantId, request.scopeHash(), request.activeAtUtc(), request.quoteScope(),
        request.requiredPolicyTypes());
  }

  public PolicyVersionView getMarginPolicyVersion(String tenantId, String policyId, String versionId,
      String viewerPermission) {
    return readPolicyVersion(tenantId, policyId, versionId, viewerPermission)
        .orElseThrow(() -> new MarginVersioningException("VERSION_NOT_FOUND"));
  }

  public MarginCompVersionManifest resolveMarginVersionForQuoteService(InternalMarginVersionResolutionRequest request) {
    Objects.requireNonNull(request, "request is required");
    requireText(request.tenantId(), "tenantId");
    requireText(request.serviceAccountId(), "serviceAccountId");
    if (!request.serviceAccount()) {
      throw new MarginVersioningException("TENANT_ACCESS_DENIED");
    }
    return resolveManifest(request.tenantId(), request.scopeHash(), request.activeAtUtc(), request.quoteScope(),
        request.requiredPolicyTypes());
  }

  public void onGovernanceChangePublished(GovernanceChangePublishedEvent event) {
    requireDurableStoreOrExplicitTestHarness();
    Objects.requireNonNull(event, "event is required");
    store.derivedManifestCache().keySet().removeIf(key -> key.tenantId().equals(event.tenantId()));
    store.auditRecords().add(AuditRecord.completed(event.tenantId(), event.policyId(), event.actorId(), event.correlationId(),
        "MARGIN_VERSION_MANIFEST_CACHE_INVALIDATED", clock));
  }

  public List<Object> outboxEvents() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.outbox());
  }

  public List<AuditRecord> auditRecords() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.auditRecords());
  }

  private void requireDurableStoreOrExplicitTestHarness() {
    store.requireAvailable();
  }

  private void rejectPublishedOverlap(PolicyVersionRef candidate) {
    long overlapping = store.publishedVersions().values().stream()
        .filter(version -> version.tenantId().equals(candidate.tenantId()))
        .filter(version -> version.policyType().equals(candidate.policyType()))
        .filter(version -> version.scope().matches(candidate.scope()))
        .filter(version -> version.effectiveWindow().overlaps(candidate.effectiveWindow()))
        .count();
    if (overlapping > 0) {
      marginVersionOverlapDetectedTotal.incrementAndGet();
      store.auditRecords().add(AuditRecord.completed(candidate.tenantId(), candidate.policyId(), "system", "overlap",
          "VERSION_EFFECTIVE_OVERLAP", clock));
      throw new MarginVersioningException("VERSION_EFFECTIVE_OVERLAP");
    }
  }

  private PolicyVersionRef selectActiveVersion(String tenantId, String policyType, Instant activeAtUtc,
      MarginResolutionScope quoteScope) {
    List<PolicyVersionRef> matches = store.publishedVersions().values().stream()
        .filter(version -> tenantId.equals(version.tenantId()))
        .filter(version -> policyType.equals(version.policyType()))
        .filter(version -> version.effectiveWindow().contains(activeAtUtc))
        .filter(version -> version.scope().matches(quoteScope))
        .sorted(Comparator.comparingInt((PolicyVersionRef version) -> version.scope().specificity()).reversed()
            .thenComparing(Comparator.comparingInt(PolicyVersionRef::priority).reversed())
            .thenComparing(Comparator.comparingInt(PolicyVersionRef::versionNumber).reversed()))
        .toList();
    if (matches.isEmpty()) {
      throw new MarginVersioningException("VERSION_MANIFEST_INCOMPLETE");
    }
    PolicyVersionRef best = matches.get(0);
    long ambiguous = matches.stream()
        .filter(version -> version.scope().specificity() == best.scope().specificity())
        .filter(version -> version.priority() == best.priority())
        .filter(version -> version.versionNumber() == best.versionNumber())
        .count();
    if (ambiguous > 1) {
      marginVersionOverlapDetectedTotal.incrementAndGet();
      throw new MarginVersioningException("VERSION_EFFECTIVE_OVERLAP");
    }
    return best;
  }

  private String publishedConfigHash(String tenantId) {
    return stableHash(store.publishedVersions().values().stream()
        .filter(version -> tenantId.equals(version.tenantId()))
        .sorted(Comparator.comparing(PolicyVersionRef::policyType)
            .thenComparing(PolicyVersionRef::policyId)
            .thenComparing(PolicyVersionRef::versionId))
        .map(PolicyVersionRef::configHash)
        .toList());
  }

  private static List<String> requiredTypes(List<String> requiredPolicyTypes) {
    if (requiredPolicyTypes == null || requiredPolicyTypes.isEmpty()) {
      throw new MarginVersioningException("VERSION_MANIFEST_INCOMPLETE");
    }
    Set<String> seen = new HashSet<>();
    List<String> result = new ArrayList<>();
    for (String type : requiredPolicyTypes) {
      requireText(type, "policyType");
      if (seen.add(type)) {
        result.add(type);
      }
    }
    result.sort(String::compareTo);
    return result;
  }

  private static Instant activeAtBucket(Instant activeAtUtc) {
    return activeAtUtc.truncatedTo(ChronoUnit.MINUTES);
  }

  private static String canonicalManifestVersions(TreeMap<String, ManifestPolicyVersion> versions) {
    StringBuilder builder = new StringBuilder();
    versions.forEach((type, version) -> builder.append(type).append('=')
        .append(version.policyId()).append(':')
        .append(version.versionId()).append(':')
        .append(version.configHash()).append(';'));
    return builder.toString();
  }

  private static String stableHash(Object... values) {
    return stableHash(List.of(values));
  }

  private static String stableHash(List<?> values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Object value : values) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '|');
      }
      byte[] hash = digest.digest();
      StringBuilder builder = new StringBuilder();
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new MarginVersioningException(field + " is required");
    }
  }

  interface Store {
    Map<VersionKey, PolicyVersionRef> publishedVersions();
    Map<ManifestCacheKey, MarginCompVersionManifest> derivedManifestCache();
    Map<String, MarginCompVersionManifest> replayManifests();
    List<Object> outbox();
    List<AuditRecord> auditRecords();

    default void requireAvailable() {}

    static Store failClosed(String component) {
      return new Store() {
        @Override public void requireAvailable() {
          ProcessLocalStatePolicy.requireDurableStoreOrExplicitTestHarness(false, component);
        }
        @Override public Map<VersionKey, PolicyVersionRef> publishedVersions() { return unavailable(); }
        @Override public Map<ManifestCacheKey, MarginCompVersionManifest> derivedManifestCache() { return unavailable(); }
        @Override public Map<String, MarginCompVersionManifest> replayManifests() { return unavailable(); }
        @Override public List<Object> outbox() { return unavailable(); }
        @Override public List<AuditRecord> auditRecords() { return unavailable(); }
        private <T> T unavailable() {
          requireAvailable();
          throw new AssertionError("unreachable");
        }
      };
    }
  }

  record VersionKey(String tenantId, String policyType, String policyId, String versionId) {}

  record ManifestCacheKey(String tenantId, String scopeHash, Instant activeAtBucket, String publishedConfigHash) {}

  public record PublishReceipt(String policyId, String versionId, List<Object> events, String auditRef) {}

  public record PolicyVersionRef(String tenantId, String policyType, String policyId, String versionId,
      int versionNumber, EffectiveWindow effectiveWindow, MarginResolutionScope scope, int priority, String configHash,
      String immutableHash) {
    public PolicyVersionRef {
      requireText(tenantId, "tenantId");
      requireText(policyType, "policyType");
      requireText(policyId, "policyId");
      requireText(versionId, "versionId");
      effectiveWindow = Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
      scope = Objects.requireNonNull(scope, "scope is required");
      requireText(configHash, "configHash");
      requireText(immutableHash, "immutableHash");
    }
  }

  public record EffectiveWindow(Instant effectiveFromUtc, Instant effectiveToUtc) {
    public EffectiveWindow {
      Objects.requireNonNull(effectiveFromUtc, "effectiveFromUtc is required");
      if (effectiveToUtc != null && !effectiveFromUtc.isBefore(effectiveToUtc)) {
        throw new MarginVersioningException("VERSION_EFFECTIVE_OVERLAP");
      }
    }

    boolean contains(Instant instant) {
      return !instant.isBefore(effectiveFromUtc) && (effectiveToUtc == null || instant.isBefore(effectiveToUtc));
    }

    boolean overlaps(EffectiveWindow other) {
      Instant thisEnd = effectiveToUtc == null ? Instant.MAX : effectiveToUtc;
      Instant otherEnd = other.effectiveToUtc == null ? Instant.MAX : other.effectiveToUtc;
      return effectiveFromUtc.isBefore(otherEnd) && other.effectiveFromUtc.isBefore(thisEnd);
    }
  }

  public record MarginResolutionScope(Map<String, String> selectors) {
    public MarginResolutionScope {
      selectors = Map.copyOf(Objects.requireNonNull(selectors, "selectors is required"));
    }

    boolean matches(MarginResolutionScope quoteScope) {
      for (Map.Entry<String, String> entry : selectors.entrySet()) {
        String value = entry.getValue();
        String quoteValue = quoteScope.selectors().get(entry.getKey());
        if (!"*".equals(value) && !Objects.equals(value, quoteValue)) {
          return false;
        }
      }
      return true;
    }

    int specificity() {
      return (int) selectors.values().stream().filter(value -> !"*".equals(value)).count();
    }

    String stableHash() {
      return MarginVersioningService.stableHash(new TreeMap<>(selectors).entrySet().stream()
          .map(entry -> entry.getKey() + "=" + entry.getValue())
          .toList());
    }
  }

  public record MarginCompVersionManifest(String tenantId, String scopeHash, Instant activeAtUtc,
      Map<String, ManifestPolicyVersion> policyVersions, String configHash, String resolverEngineVersion,
      String resultHash, String replayRef, String correlationId) {}

  public record ManifestPolicyVersion(String policyType, String policyId, String versionId, int versionNumber,
      String scopeHash, String configHash, Instant effectiveFromUtc) {
    static ManifestPolicyVersion from(PolicyVersionRef version) {
      return new ManifestPolicyVersion(version.policyType(), version.policyId(), version.versionId(),
          version.versionNumber(), version.scope().stableHash(), version.configHash(),
          version.effectiveWindow().effectiveFromUtc());
    }
  }

  public record PolicyVersionView(String policyType, String policyId, String versionId, int versionNumber,
      String scopeHash, String configHash, Instant effectiveFromUtc, Instant effectiveToUtc, boolean fieldFiltered) {
    static PolicyVersionView from(PolicyVersionRef version, boolean sensitiveAllowed) {
      return new PolicyVersionView(version.policyType(), version.policyId(), version.versionId(),
          version.versionNumber(), version.scope().stableHash(), sensitiveAllowed ? version.configHash() : null,
          version.effectiveWindow().effectiveFromUtc(), version.effectiveWindow().effectiveToUtc(), !sensitiveAllowed);
    }
  }

  public record MarginVersionManifestRequest(String scopeHash, Instant activeAtUtc,
      MarginResolutionScope quoteScope, List<String> requiredPolicyTypes) {
    public MarginVersionManifestRequest {
      requireText(scopeHash, "scopeHash");
      activeAtUtc = Objects.requireNonNull(activeAtUtc, "activeAtUtc is required");
      quoteScope = Objects.requireNonNull(quoteScope, "quoteScope is required");
      requiredPolicyTypes = List.copyOf(Objects.requireNonNull(requiredPolicyTypes, "requiredPolicyTypes is required"));
    }
  }

  public record InternalMarginVersionResolutionRequest(String tenantId, String serviceAccountId,
      boolean serviceAccount, String scopeHash, Instant activeAtUtc, MarginResolutionScope quoteScope,
      List<String> requiredPolicyTypes) {
    public InternalMarginVersionResolutionRequest {
      requireText(tenantId, "tenantId");
      requireText(serviceAccountId, "serviceAccountId");
      requireText(scopeHash, "scopeHash");
      activeAtUtc = Objects.requireNonNull(activeAtUtc, "activeAtUtc is required");
      quoteScope = Objects.requireNonNull(quoteScope, "quoteScope is required");
      requiredPolicyTypes = List.copyOf(Objects.requireNonNull(requiredPolicyTypes, "requiredPolicyTypes is required"));
    }
  }

  public record GovernanceChangePublishedEvent(String tenantId, String policyType, String policyId, String versionId,
      String actorId, String correlationId, Instant occurredAt) {}

  public record MarginCompVersionManifestResolvedEvent(String tenantId, String scopeHash, Instant activeAtUtc,
      String resultHash, String resolverEngineVersion, Instant occurredAt) {}

  public record AuditRecord(String tenantId, String subjectRef, String actorId, String correlationId, String action,
      Instant recordedAt) {
    static AuditRecord completed(String tenantId, String subjectRef, String actorId, String correlationId, String action,
        Clock clock) {
      return new AuditRecord(tenantId, subjectRef, actorId, correlationId, action, Instant.now(clock));
    }
  }

  public static final class MarginVersioningException extends RuntimeException {
    public MarginVersioningException(String message) {
      super(message);
    }
  }
}
