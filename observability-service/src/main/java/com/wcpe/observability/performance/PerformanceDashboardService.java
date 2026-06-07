package com.wcpe.observability.performance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PerformanceDashboardService {
  private static final Set<String> SENSITIVE_TOKENS = Set.of(
      "borrower", "ssn", "password", "secret", "token", "customer name", "raw request", "loan_number");

  private final Clock clock;
  private final List<RunbookMetadata> runbooks;

  public PerformanceDashboardService(Clock clock, List<RunbookMetadata> runbooks) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.runbooks = List.copyOf(Objects.requireNonNull(runbooks, "runbooks are required"));
    this.runbooks.forEach(RunbookMetadata::validateSafe);
  }

  public PerformanceDashboardSummary summarize(
      PerformanceDashboardRequest request,
      List<PerformanceMetricSnapshot> metricSnapshots,
      List<PerformanceAlertSnapshot> alertSnapshots) {
    DashboardData data = selectData(request, metricSnapshots, alertSnapshots, null);
    List<PanelSummary> panelSummaries = data.panels().stream()
        .map(panel -> summarizePanel(panel, data.metricsByPanel().getOrDefault(panel, List.of()),
            data.alertsByPanel().getOrDefault(panel, List.of())))
        .sorted(Comparator.comparing(summary -> summary.panel().name()))
        .toList();

    return new PerformanceDashboardSummary(
        request.tenantId(),
        tenantLabel(request),
        clock.instant(),
        aggregateFreshness(panelSummaries.stream().map(PanelSummary::dataFreshness).toList()),
        panelSummaries.stream().anyMatch(PanelSummary::partial),
        panelSummaries,
        runbooksFor(data.alerts()));
  }

  public PerformanceDashboardPanel panelData(
      PerformanceDashboardRequest request,
      DashboardPanel panel,
      List<PerformanceMetricSnapshot> metricSnapshots,
      List<PerformanceAlertSnapshot> alertSnapshots) {
    Objects.requireNonNull(panel, "panel is required");
    DashboardData data = selectData(request, metricSnapshots, alertSnapshots, panel);
    List<PerformanceMetricSnapshot> metrics = data.metricsByPanel().getOrDefault(panel, List.of());
    List<PerformanceAlertSnapshot> alerts = data.alertsByPanel().getOrDefault(panel, List.of());
    PanelSummary summary = summarizePanel(panel, metrics, alerts);

    return new PerformanceDashboardPanel(
        panel,
        summary.dataFreshness(),
        summary.source(),
        summary.partial(),
        metrics.stream()
            .map(metric -> DashboardMetric.from(metric, tenantLabel(request)))
            .sorted(Comparator.comparing(DashboardMetric::metricName))
            .toList(),
        alerts.stream()
            .map(alert -> DashboardAlert.from(alert, tenantLabel(request)))
            .sorted(Comparator.comparing(DashboardAlert::alertKey))
            .toList(),
        runbooksFor(alerts));
  }

  public RunbookMetadata runbook(String slug) {
    String safeSlug = requireSafeText(slug, "slug");
    return runbooks.stream()
        .filter(runbook -> runbook.slug().equals(safeSlug))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("runbook not found"));
  }

  private DashboardData selectData(
      PerformanceDashboardRequest request,
      List<PerformanceMetricSnapshot> metricSnapshots,
      List<PerformanceAlertSnapshot> alertSnapshots,
      DashboardPanel forcedPanel) {
    Objects.requireNonNull(request, "request is required").validateTenantAccess();
    List<PerformanceMetricSnapshot> safeMetrics = List.copyOf(Objects.requireNonNull(metricSnapshots, "metrics are required"));
    List<PerformanceAlertSnapshot> safeAlerts = List.copyOf(Objects.requireNonNull(alertSnapshots, "alerts are required"));
    safeMetrics.forEach(PerformanceMetricSnapshot::validateSafe);
    safeAlerts.forEach(PerformanceAlertSnapshot::validateSafe);

    Set<DashboardPanel> selectedPanels = forcedPanel == null
        ? request.selectedPanels()
        : EnumSet.of(forcedPanel);
    List<PerformanceMetricSnapshot> filteredMetrics = safeMetrics.stream()
        .filter(metric -> metric.tenantId().equals(request.tenantId()))
        .filter(metric -> selectedPanels.isEmpty() || selectedPanels.contains(metric.panel()))
        .filter(metric -> request.matchesWindow(metric.windowStart(), metric.windowEnd()))
        .filter(request::matchesDimensions)
        .toList();
    List<PerformanceAlertSnapshot> filteredAlerts = safeAlerts.stream()
        .filter(alert -> alert.tenantId().equals(request.tenantId()))
        .filter(alert -> selectedPanels.isEmpty() || selectedPanels.contains(alert.panel()))
        .filter(alert -> request.severities().isEmpty() || request.severities().contains(alert.severity()))
        .filter(alert -> request.matchesWindow(alert.startedAt(), alert.resolvedAt()))
        .toList();

    Set<DashboardPanel> panels = new HashSet<>(selectedPanels);
    if (panels.isEmpty()) {
      filteredMetrics.stream().map(PerformanceMetricSnapshot::panel).forEach(panels::add);
      filteredAlerts.stream().map(PerformanceAlertSnapshot::panel).forEach(panels::add);
    }
    return new DashboardData(
        panels,
        filteredMetrics,
        filteredAlerts,
        filteredMetrics.stream().collect(Collectors.groupingBy(PerformanceMetricSnapshot::panel)),
        filteredAlerts.stream().collect(Collectors.groupingBy(PerformanceAlertSnapshot::panel)));
  }

  private PanelSummary summarizePanel(
      DashboardPanel panel,
      List<PerformanceMetricSnapshot> metrics,
      List<PerformanceAlertSnapshot> alerts) {
    FreshnessStatus freshness = aggregateFreshness(metrics.stream()
        .map(PerformanceMetricSnapshot::freshnessStatus)
        .toList());
    boolean partial = metrics.isEmpty() || freshness != FreshnessStatus.FRESH
        || metrics.stream().anyMatch(PerformanceMetricSnapshot::partial);
    String source = metrics.stream()
        .map(PerformanceMetricSnapshot::source)
        .distinct()
        .sorted()
        .collect(Collectors.joining(","));
    if (source.isBlank()) {
      source = "no-service-local-snapshot";
    }
    long activeAlertCount = alerts.stream().filter(alert -> alert.status() == AlertStatus.ACTIVE).count();
    return new PanelSummary(panel, metrics.size(), activeAlertCount, freshness, partial, source);
  }

  private FreshnessStatus aggregateFreshness(List<FreshnessStatus> statuses) {
    if (statuses.isEmpty()) {
      return FreshnessStatus.NO_DATA;
    }
    if (statuses.contains(FreshnessStatus.STALE)) {
      return FreshnessStatus.STALE;
    }
    if (statuses.contains(FreshnessStatus.PARTIAL)) {
      return FreshnessStatus.PARTIAL;
    }
    return FreshnessStatus.FRESH;
  }

  private List<RunbookMetadata> runbooksFor(List<PerformanceAlertSnapshot> alerts) {
    Set<String> slugs = alerts.stream()
        .map(PerformanceAlertSnapshot::runbookSlug)
        .filter(slug -> slug != null && !slug.isBlank())
        .collect(Collectors.toCollection(HashSet::new));
    return runbooks.stream()
        .filter(runbook -> slugs.contains(runbook.slug()))
        .sorted(Comparator.comparing(RunbookMetadata::slug))
        .toList();
  }

  private String tenantLabel(PerformanceDashboardRequest request) {
    if (request.actorRole() == DashboardActorRole.PLATFORM_OPERATOR) {
      return "tenant-hash-" + hashTenant(request.tenantId().toString());
    }
    return "current-tenant";
  }

  private static String hashTenant(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (int index = 0; index < 6; index++) {
        out.append(String.format("%02x", digest[index]));
      }
      return out.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private static String requireSafeText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    String trimmed = value.trim();
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (SENSITIVE_TOKENS.stream().anyMatch(lower::contains)) {
      throw new IllegalArgumentException(field + " contains sensitive or raw borrower metadata");
    }
    if (!trimmed.matches("[A-Za-z0-9 ._:/?#&=-]+") || trimmed.length() > 180) {
      throw new IllegalArgumentException(field + " must be sanitized dashboard text");
    }
    return trimmed;
  }

  public enum DashboardPanel {
    EXECUTIVE_SLO,
    PRICING_API_LATENCY,
    REDIS_CACHE_HEALTH,
    REFERENCE_CACHE,
    PRICING_RESULT_CACHE,
    SCENARIO_HASH_REPLAY,
    CACHE_INVALIDATION,
    DB_INDEX_HEALTH,
    LOAD_TEST_HISTORY,
    RATE_LIMITING,
    BACKPRESSURE,
    ALERTS_RUNBOOKS
  }

  public enum FreshnessStatus {
    FRESH,
    PARTIAL,
    STALE,
    NO_DATA
  }

  public enum DashboardActorRole {
    PLATFORM_OPERATOR,
    TENANT_ADMIN,
    SUPPORT_VIEWER
  }

  public enum AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
  }

  public enum AlertStatus {
    ACTIVE,
    RESOLVED,
    ACKNOWLEDGED
  }

  public record PerformanceDashboardRequest(
      UUID tenantId,
      UUID actorTenantId,
      DashboardActorRole actorRole,
      Instant from,
      Instant to,
      Set<DashboardPanel> selectedPanels,
      Set<AlertSeverity> severities,
      Map<String, String> filters) {
    public PerformanceDashboardRequest {
      tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
      actorTenantId = Objects.requireNonNull(actorTenantId, "actorTenantId is required");
      actorRole = Objects.requireNonNull(actorRole, "actorRole is required");
      selectedPanels = selectedPanels == null ? Set.of() : Set.copyOf(selectedPanels);
      severities = severities == null ? Set.of() : Set.copyOf(severities);
      filters = filters == null ? Map.of() : safeMap(filters, "filter");
      if (from != null && to != null && from.isAfter(to)) {
        throw new IllegalArgumentException("from must not be after to");
      }
    }

    void validateTenantAccess() {
      if (actorRole != DashboardActorRole.PLATFORM_OPERATOR && !tenantId.equals(actorTenantId)) {
        throw new SecurityException("TENANT_ACCESS_DENIED");
      }
    }

    boolean matchesWindow(Instant start, Instant end) {
      Instant effectiveStart = start == null ? Instant.MIN : start;
      Instant effectiveEnd = end == null ? effectiveStart : end;
      return (from == null || !effectiveEnd.isBefore(from)) && (to == null || !effectiveStart.isAfter(to));
    }

    boolean matchesDimensions(PerformanceMetricSnapshot metric) {
      return filters.entrySet().stream()
          .allMatch(entry -> entry.getValue().equals(metric.dimensions().get(entry.getKey())));
    }
  }

  public record PerformanceMetricSnapshot(
      UUID id,
      UUID tenantId,
      DashboardPanel panel,
      String metricName,
      Map<String, String> dimensions,
      BigDecimal valueNumeric,
      String valueText,
      Instant windowStart,
      Instant windowEnd,
      FreshnessStatus freshnessStatus,
      String source,
      boolean partial,
      Instant createdAt) {
    public PerformanceMetricSnapshot {
      id = Objects.requireNonNull(id, "id is required");
      tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
      panel = Objects.requireNonNull(panel, "panel is required");
      metricName = requireSafeText(metricName, "metricName");
      dimensions = dimensions == null ? Map.of() : safeMap(dimensions, "dimension");
      valueText = valueText == null ? null : requireSafeText(valueText, "valueText");
      windowStart = Objects.requireNonNull(windowStart, "windowStart is required");
      windowEnd = Objects.requireNonNull(windowEnd, "windowEnd is required");
      if (windowStart.isAfter(windowEnd)) {
        throw new IllegalArgumentException("windowStart must not be after windowEnd");
      }
      freshnessStatus = Objects.requireNonNull(freshnessStatus, "freshnessStatus is required");
      source = requireSafeText(source, "source");
      createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    void validateSafe() {
      requireSafeText(metricName, "metricName");
      requireSafeText(source, "source");
    }
  }

  public record PerformanceAlertSnapshot(
      UUID id,
      UUID tenantId,
      DashboardPanel panel,
      AlertSeverity severity,
      String alertKey,
      AlertStatus status,
      String summary,
      String runbookSlug,
      Instant startedAt,
      Instant resolvedAt,
      String correlationId) {
    public PerformanceAlertSnapshot {
      id = Objects.requireNonNull(id, "id is required");
      tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
      panel = Objects.requireNonNull(panel, "panel is required");
      severity = Objects.requireNonNull(severity, "severity is required");
      alertKey = requireSafeText(alertKey, "alertKey");
      status = Objects.requireNonNull(status, "status is required");
      summary = requireSafeText(summary, "summary");
      runbookSlug = runbookSlug == null ? null : requireSafeText(runbookSlug, "runbookSlug");
      startedAt = Objects.requireNonNull(startedAt, "startedAt is required");
      correlationId = requireSafeText(correlationId, "correlationId");
    }

    void validateSafe() {
      requireSafeText(alertKey, "alertKey");
      requireSafeText(summary, "summary");
      requireSafeText(correlationId, "correlationId");
    }
  }

  public record RunbookMetadata(String slug, String title, String link, String summary) {
    public RunbookMetadata {
      slug = requireSafeText(slug, "slug");
      title = requireSafeText(title, "title");
      link = requireSafeText(link, "link");
      summary = requireSafeText(summary, "summary");
    }

    void validateSafe() {
      requireSafeText(slug, "slug");
      requireSafeText(title, "title");
      requireSafeText(link, "link");
      requireSafeText(summary, "summary");
    }
  }

  public record PerformanceDashboardSummary(
      UUID tenantId,
      String tenantLabel,
      Instant generatedAt,
      FreshnessStatus dataFreshness,
      boolean partial,
      List<PanelSummary> panels,
      List<RunbookMetadata> runbooks) {
    public PerformanceDashboardSummary {
      panels = List.copyOf(panels);
      runbooks = List.copyOf(runbooks);
    }
  }

  public record PanelSummary(
      DashboardPanel panel,
      int metricCount,
      long activeAlertCount,
      FreshnessStatus dataFreshness,
      boolean partial,
      String source) {}

  public record PerformanceDashboardPanel(
      DashboardPanel panel,
      FreshnessStatus dataFreshness,
      String source,
      boolean partial,
      List<DashboardMetric> metrics,
      List<DashboardAlert> alerts,
      List<RunbookMetadata> runbooks) {
    public PerformanceDashboardPanel {
      metrics = List.copyOf(metrics);
      alerts = List.copyOf(alerts);
      runbooks = List.copyOf(runbooks);
    }
  }

  public record DashboardMetric(
      String tenantLabel,
      String metricName,
      Map<String, String> dimensions,
      BigDecimal valueNumeric,
      String valueText,
      FreshnessStatus freshnessStatus,
      boolean partial) {
    static DashboardMetric from(PerformanceMetricSnapshot snapshot, String tenantLabel) {
      return new DashboardMetric(
          tenantLabel,
          snapshot.metricName(),
          snapshot.dimensions(),
          snapshot.valueNumeric(),
          snapshot.valueText(),
          snapshot.freshnessStatus(),
          snapshot.partial());
    }
  }

  public record DashboardAlert(
      String tenantLabel,
      AlertSeverity severity,
      String alertKey,
      AlertStatus status,
      String summary,
      String runbookSlug,
      String correlationId) {
    static DashboardAlert from(PerformanceAlertSnapshot snapshot, String tenantLabel) {
      return new DashboardAlert(
          tenantLabel,
          snapshot.severity(),
          snapshot.alertKey(),
          snapshot.status(),
          snapshot.summary(),
          snapshot.runbookSlug(),
          snapshot.correlationId());
    }
  }

  private record DashboardData(
      Set<DashboardPanel> panels,
      List<PerformanceMetricSnapshot> metrics,
      List<PerformanceAlertSnapshot> alerts,
      Map<DashboardPanel, List<PerformanceMetricSnapshot>> metricsByPanel,
      Map<DashboardPanel, List<PerformanceAlertSnapshot>> alertsByPanel) {
    DashboardData {
      panels = Set.copyOf(panels);
      metrics = List.copyOf(metrics);
      alerts = List.copyOf(alerts);
      metricsByPanel = copyMetricMap(metricsByPanel);
      alertsByPanel = copyAlertMap(alertsByPanel);
    }
  }

  private static Map<String, String> safeMap(Map<String, String> values, String field) {
    Map<String, String> sanitized = new LinkedHashMap<>();
    values.forEach((key, value) -> sanitized.put(
        requireSafeText(key, field + " key"),
        requireSafeText(value, field + " value")));
    return Map.copyOf(sanitized);
  }

  private static Map<DashboardPanel, List<PerformanceMetricSnapshot>> copyMetricMap(
      Map<DashboardPanel, List<PerformanceMetricSnapshot>> source) {
    Map<DashboardPanel, List<PerformanceMetricSnapshot>> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
    return Map.copyOf(copy);
  }

  private static Map<DashboardPanel, List<PerformanceAlertSnapshot>> copyAlertMap(
      Map<DashboardPanel, List<PerformanceAlertSnapshot>> source) {
    Map<DashboardPanel, List<PerformanceAlertSnapshot>> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
    return Map.copyOf(copy);
  }
}
