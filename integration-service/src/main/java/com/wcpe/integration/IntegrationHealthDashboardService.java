package com.wcpe.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class IntegrationHealthDashboardService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/integration-health";
  public static final String READ_PERMISSION = "integrations.health.read";
  public static final String SUMMARY_PATH = BASE_PATH + "/summary";
  public static final String COMPONENTS_PATH = BASE_PATH + "/components";
  public static final String INCIDENTS_PATH = BASE_PATH + "/incidents";
  public static final String METRICS_PATH = BASE_PATH + "/metrics";
  public static final String HEALTH_STATUS_CHANGED_EVENT_TYPE = "integration.health-status.changed.v1";
  public static final List<String> RESPONSE_FIELDS =
      List.of("component", "status", "lastSuccessAt", "lastFailureAt", "lagSeconds", "errorCount", "dlqCount", "credentialExpiryCount", "sloBreached", "actionLinks", "capturedAt");

  private static final List<IntegrationComponentType> DASHBOARD_COMPONENTS =
      List.of(
          IntegrationComponentType.CHANNEL_API,
          IntegrationComponentType.LOS_QUOTES,
          IntegrationComponentType.WEBHOOK_DELIVERY,
          IntegrationComponentType.INVESTOR_API,
          IntegrationComponentType.SFTP_FEEDS,
          IntegrationComponentType.CREDENTIALS,
          IntegrationComponentType.DLQ);

  private final Clock clock;
  private final Map<String, IntegrationHealthSnapshot> snapshots = new HashMap<>();
  private final List<IntegrationHealthEvent> outboxEvents = new ArrayList<>();
  private final Map<String, Long> metrics = new HashMap<>();

  public IntegrationHealthDashboardService() {
    this(Clock.systemUTC());
  }

  public IntegrationHealthDashboardService(Clock clock) {
    this.clock = clock;
  }

  public HealthResult<IntegrationHealthSnapshot> recordSnapshot(RecordHealthSnapshotCommand command) {
    HealthError validation = validate(command);
    if (validation != null) {
      return HealthResult.failure(validation);
    }

    Map<IntegrationComponentType, ComponentHealth> components = new EnumMap<>(IntegrationComponentType.class);
    for (ComponentHealth component : command.components()) {
      components.put(component.componentType(), sanitize(component));
    }

    Instant capturedAt = command.capturedAt() == null ? clock.instant() : command.capturedAt();
    IntegrationHealthSnapshot existing = latestSnapshot(command.tenantId()).orElse(null);
    HealthStatus rollup = rollup(components.values());
    IntegrationHealthSnapshot snapshot = new IntegrationHealthSnapshot(command.tenantId(), command.snapshotId(), capturedAt, Map.copyOf(components), rollup, command.correlationId());
    snapshots.put(snapshotKey(command.tenantId(), command.snapshotId()), snapshot);
    recordMetric("integration_health_snapshots_total");
    recordMetric("integration_health_components_degraded", components.values().stream().filter(component -> component.status().severityRank() > HealthStatus.HEALTHY.severityRank()).count());
    if (existing == null || existing.rollupStatus() != snapshot.rollupStatus()) {
      outboxEvents.add(new IntegrationHealthEvent(HEALTH_STATUS_CHANGED_EVENT_TYPE, command.tenantId(), command.snapshotId(), snapshot.rollupStatus(), command.actorId(), command.correlationId(), capturedAt));
    }
    return HealthResult.success(snapshot);
  }

  public HealthResult<IntegrationHealthSummary> summary(String tenantId, String correlationId) {
    if (isBlank(tenantId)) {
      return HealthResult.failure(error("400", "VALIDATION_FAILED", "tenantId is required", correlationId));
    }
    Optional<IntegrationHealthSnapshot> latest = latestSnapshot(tenantId);
    if (latest.isEmpty()) {
      return HealthResult.failure(error("404", "NOT_FOUND", "Integration health snapshot was not found", correlationId));
    }
    IntegrationHealthSnapshot snapshot = latest.orElseThrow();
    List<ComponentHealthResponse> components = componentResponses(snapshot);
    long degradedCount = components.stream().filter(component -> component.status().severityRank() > HealthStatus.HEALTHY.severityRank()).count();
    long sloBreaches = components.stream().filter(ComponentHealthResponse::sloBreached).count();
    long snapshotAgeSeconds = Math.max(0, Duration.between(snapshot.capturedAt(), clock.instant()).toSeconds());
    recordMetric("integration_health_summary_reads_total");
    return HealthResult.success(new IntegrationHealthSummary(tenantId, snapshot.snapshotId(), snapshot.rollupStatus(), snapshot.capturedAt(), snapshotAgeSeconds, components.size(), degradedCount, sloBreaches, correlationId));
  }

  public List<ComponentHealthResponse> components(String tenantId) {
    return latestSnapshot(tenantId).map(this::componentResponses).orElse(List.of());
  }

  public List<IntegrationIncident> incidents(String tenantId) {
    return latestSnapshot(tenantId)
        .map(snapshot -> componentResponses(snapshot).stream().filter(component -> component.status().severityRank() >= HealthStatus.DEGRADED.severityRank()).map(this::incident).toList())
        .orElse(List.of());
  }

  public List<IntegrationHealthMetricPoint> metrics(String tenantId, IntegrationComponentType componentType) {
    if (isBlank(tenantId)) {
      return List.of();
    }
    return snapshots.values().stream()
        .filter(snapshot -> snapshot.tenantId().equals(tenantId))
        .filter(snapshot -> componentType == null || snapshot.components().containsKey(componentType))
        .sorted(Comparator.comparing(IntegrationHealthSnapshot::capturedAt))
        .map(snapshot -> metricPoint(snapshot, componentType))
        .toList();
  }

  public List<IntegrationHealthEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public Map<String, Long> dashboardMetrics() {
    return Map.copyOf(metrics);
  }

  public static ComponentHealth component(
      IntegrationComponentType componentType,
      HealthStatus status,
      Instant lastSuccessAt,
      Instant lastFailureAt,
      long lagSeconds,
      long errorCount,
      long dlqCount,
      long credentialExpiryCount,
      boolean sloBreached,
      Map<String, String> summary,
      List<ActionLink> actionLinks) {
    return new ComponentHealth(componentType, status, lastSuccessAt, lastFailureAt, Math.max(0, lagSeconds), Math.max(0, errorCount), Math.max(0, dlqCount), Math.max(0, credentialExpiryCount), sloBreached, safeSummary(summary), safeActionLinks(actionLinks));
  }

  private HealthError validate(RecordHealthSnapshotCommand command) {
    if (command == null || isBlank(command.tenantId()) || isBlank(command.snapshotId()) || isBlank(command.actorId()) || isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "tenantId, snapshotId, actorId, and correlationId are required", command == null ? "" : command.correlationId());
    }
    if (command.components() == null || command.components().isEmpty()) {
      return error("400", "VALIDATION_FAILED", "At least one component health item is required", command.correlationId());
    }
    for (ComponentHealth component : command.components()) {
      if (component == null || component.componentType() == null || component.status() == null) {
        return error("400", "VALIDATION_FAILED", "Component type and status are required", command.correlationId());
      }
    }
    return null;
  }

  private ComponentHealth sanitize(ComponentHealth component) {
    return component(
        component.componentType(),
        component.status(),
        component.lastSuccessAt(),
        component.lastFailureAt(),
        component.lagSeconds(),
        component.errorCount(),
        component.dlqCount(),
        component.credentialExpiryCount(),
        component.sloBreached(),
        component.summary(),
        component.actionLinks());
  }

  private List<ComponentHealthResponse> componentResponses(IntegrationHealthSnapshot snapshot) {
    return DASHBOARD_COMPONENTS.stream()
        .map(componentType -> response(snapshot, componentType, snapshot.components().get(componentType)))
        .sorted(Comparator.comparing(ComponentHealthResponse::componentType))
        .toList();
  }

  private ComponentHealthResponse response(IntegrationHealthSnapshot snapshot, IntegrationComponentType componentType, ComponentHealth component) {
    if (component == null) {
      return new ComponentHealthResponse(componentType, HealthStatus.UNKNOWN, null, null, 0, 0, 0, 0, false, Map.of(), List.of(), snapshot.capturedAt(), snapshot.correlationId());
    }
    return new ComponentHealthResponse(
        componentType,
        component.status(),
        component.lastSuccessAt(),
        component.lastFailureAt(),
        component.lagSeconds(),
        component.errorCount(),
        component.dlqCount(),
        component.credentialExpiryCount(),
        component.sloBreached(),
        component.summary(),
        component.actionLinks(),
        snapshot.capturedAt(),
        snapshot.correlationId());
  }

  private IntegrationIncident incident(ComponentHealthResponse component) {
    return new IntegrationIncident(component.componentType(), component.status(), component.sloBreached(), component.lastFailureAt(), component.actionLinks(), component.capturedAt(), component.correlationId());
  }

  private IntegrationHealthMetricPoint metricPoint(IntegrationHealthSnapshot snapshot, IntegrationComponentType componentType) {
    if (componentType == null) {
      return new IntegrationHealthMetricPoint(snapshot.capturedAt(), snapshot.rollupStatus(), 0, 0, 0, 0);
    }
    ComponentHealth component = snapshot.components().get(componentType);
    if (component == null) {
      return new IntegrationHealthMetricPoint(snapshot.capturedAt(), HealthStatus.UNKNOWN, 0, 0, 0, 0);
    }
    return new IntegrationHealthMetricPoint(snapshot.capturedAt(), component.status(), component.lagSeconds(), component.errorCount(), component.dlqCount(), component.credentialExpiryCount());
  }

  private Optional<IntegrationHealthSnapshot> latestSnapshot(String tenantId) {
    if (isBlank(tenantId)) {
      return Optional.empty();
    }
    return snapshots.values().stream().filter(snapshot -> snapshot.tenantId().equals(tenantId)).max(Comparator.comparing(IntegrationHealthSnapshot::capturedAt).thenComparing(IntegrationHealthSnapshot::snapshotId));
  }

  private static HealthStatus rollup(Iterable<ComponentHealth> components) {
    HealthStatus rollup = HealthStatus.UNKNOWN;
    boolean found = false;
    for (ComponentHealth component : components) {
      if (component == null || component.status() == null) {
        continue;
      }
      found = true;
      if (rollup == HealthStatus.UNKNOWN || component.status().severityRank() > rollup.severityRank()) {
        rollup = component.status();
      }
    }
    return found ? rollup : HealthStatus.UNKNOWN;
  }

  private static Map<String, String> safeSummary(Map<String, String> summary) {
    if (summary == null || summary.isEmpty()) {
      return Map.of();
    }
    Map<String, String> safe = new HashMap<>();
    for (Map.Entry<String, String> entry : summary.entrySet()) {
      String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
      if (key.contains("payload") || key.contains("secret") || key.contains("credential") || key.contains("borrower") || key.contains("raw")) {
        safe.put(entry.getKey(), "<redacted>");
      } else {
        safe.put(entry.getKey(), entry.getValue());
      }
    }
    return Map.copyOf(safe);
  }

  private static List<ActionLink> safeActionLinks(List<ActionLink> actionLinks) {
    if (actionLinks == null || actionLinks.isEmpty()) {
      return List.of();
    }
    return actionLinks.stream().filter(link -> link != null && !isBlank(link.label()) && !isBlank(link.href())).map(link -> new ActionLink(link.label().trim(), link.href().trim())).toList();
  }

  private static String snapshotKey(String tenantId, String snapshotId) {
    return tenantId + ":" + snapshotId;
  }

  private void recordMetric(String name) {
    recordMetric(name, 1);
  }

  private void recordMetric(String name, long amount) {
    metrics.merge(name, amount, Long::sum);
  }

  private static HealthError error(String code, String reason, String message, String correlationId) {
    return new HealthError(code, reason, message, correlationId);
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public enum IntegrationComponentType {
    CHANNEL_API,
    LOS_QUOTES,
    WEBHOOK_DELIVERY,
    INVESTOR_API,
    SFTP_FEEDS,
    CREDENTIALS,
    DLQ
  }

  public enum HealthStatus {
    HEALTHY(0),
    DEGRADED(1),
    DOWN(2),
    UNKNOWN(-1);

    private final int severityRank;

    HealthStatus(int severityRank) {
      this.severityRank = severityRank;
    }

    int severityRank() {
      return severityRank;
    }
  }

  public record RecordHealthSnapshotCommand(String tenantId, String snapshotId, List<ComponentHealth> components, Instant capturedAt, String actorId, String correlationId) {}

  public record ComponentHealth(
      IntegrationComponentType componentType,
      HealthStatus status,
      Instant lastSuccessAt,
      Instant lastFailureAt,
      long lagSeconds,
      long errorCount,
      long dlqCount,
      long credentialExpiryCount,
      boolean sloBreached,
      Map<String, String> summary,
      List<ActionLink> actionLinks) {}

  public record IntegrationHealthSnapshot(String tenantId, String snapshotId, Instant capturedAt, Map<IntegrationComponentType, ComponentHealth> components, HealthStatus rollupStatus, String correlationId) {}

  public record ComponentHealthResponse(
      IntegrationComponentType componentType,
      HealthStatus status,
      Instant lastSuccessAt,
      Instant lastFailureAt,
      long lagSeconds,
      long errorCount,
      long dlqCount,
      long credentialExpiryCount,
      boolean sloBreached,
      Map<String, String> summary,
      List<ActionLink> actionLinks,
      Instant capturedAt,
      String correlationId) {}

  public record IntegrationHealthSummary(String tenantId, String snapshotId, HealthStatus rollupStatus, Instant capturedAt, long snapshotAgeSeconds, int componentCount, long degradedComponentCount, long sloBreachCount, String correlationId) {}

  public record IntegrationIncident(IntegrationComponentType componentType, HealthStatus status, boolean sloBreached, Instant lastFailureAt, List<ActionLink> actionLinks, Instant capturedAt, String correlationId) {}

  public record IntegrationHealthMetricPoint(Instant capturedAt, HealthStatus status, long lagSeconds, long errorCount, long dlqCount, long credentialExpiryCount) {}

  public record IntegrationHealthEvent(String eventType, String tenantId, String snapshotId, HealthStatus rollupStatus, String actorId, String correlationId, Instant occurredAt) {}

  public record ActionLink(String label, String href) {}

  public record HealthError(String code, String reason, String message, String correlationId) {}

  public record HealthResult<T>(boolean valid, Optional<T> value, Optional<HealthError> error) {
    static <T> HealthResult<T> success(T value) {
      return new HealthResult<>(true, Optional.of(value), Optional.empty());
    }

    static <T> HealthResult<T> failure(HealthError error) {
      return new HealthResult<>(false, Optional.empty(), Optional.of(error));
    }
  }
}
