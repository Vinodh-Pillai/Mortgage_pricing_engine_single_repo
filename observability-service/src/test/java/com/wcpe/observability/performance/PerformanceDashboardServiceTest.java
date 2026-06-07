package com.wcpe.observability.performance;

import static com.wcpe.observability.performance.PerformanceDashboardService.AlertSeverity.CRITICAL;
import static com.wcpe.observability.performance.PerformanceDashboardService.AlertStatus.ACTIVE;
import static com.wcpe.observability.performance.PerformanceDashboardService.DashboardActorRole.PLATFORM_OPERATOR;
import static com.wcpe.observability.performance.PerformanceDashboardService.DashboardActorRole.TENANT_ADMIN;
import static com.wcpe.observability.performance.PerformanceDashboardService.DashboardPanel.ALERTS_RUNBOOKS;
import static com.wcpe.observability.performance.PerformanceDashboardService.DashboardPanel.PRICING_API_LATENCY;
import static com.wcpe.observability.performance.PerformanceDashboardService.DashboardPanel.REDIS_CACHE_HEALTH;
import static com.wcpe.observability.performance.PerformanceDashboardService.FreshnessStatus.FRESH;
import static com.wcpe.observability.performance.PerformanceDashboardService.FreshnessStatus.PARTIAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerformanceDashboardServiceTest {
  private static final Instant NOW = Instant.parse("2026-06-07T22:30:00Z");
  private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private final PerformanceDashboardService service = new PerformanceDashboardService(
      Clock.fixed(NOW, ZoneOffset.UTC),
      List.of(new PerformanceDashboardService.RunbookMetadata(
          "redis-outage",
          "Redis outage",
          "runbooks/redis-outage",
          "Service-local recovery metadata for a Redis outage")));

  @Test
  void summarizesTenantScopedPanelsWithFreshnessPartialAndRunbookMetadata() {
    PerformanceDashboardService.PerformanceDashboardRequest request = tenantRequest(
        Set.of(PRICING_API_LATENCY, REDIS_CACHE_HEALTH, ALERTS_RUNBOOKS), Map.of());

    PerformanceDashboardService.PerformanceDashboardSummary summary = service.summarize(
        request,
        List.of(
            metric(PRICING_API_LATENCY, "pricing.quote.latency.p95", FRESH, false,
                Map.of("endpoint_group", "quote", "channel", "retail")),
            metric(REDIS_CACHE_HEALTH, "redis.cache.fallback.count", PARTIAL, true,
                Map.of("cache_namespace", "pricing-results")),
            otherTenantMetric()),
        List.of(alert()));

    assertEquals(TENANT, summary.tenantId());
    assertEquals("current-tenant", summary.tenantLabel());
    assertEquals(PARTIAL, summary.dataFreshness());
    assertTrue(summary.partial());
    assertEquals(3, summary.panels().size());
    assertTrue(summary.panels().stream().anyMatch(panel -> panel.panel() == REDIS_CACHE_HEALTH
        && panel.partial()
        && panel.source().equals("service-local-fixture")));
    assertEquals(List.of("redis-outage"), summary.runbooks().stream().map(
        PerformanceDashboardService.RunbookMetadata::slug).toList());
  }

  @Test
  void filtersPanelDataByDimensionsAndShapesTenantLabelsForPlatformOperator() {
    PerformanceDashboardService.PerformanceDashboardRequest request = new PerformanceDashboardService
        .PerformanceDashboardRequest(
            TENANT,
            OTHER_TENANT,
            PLATFORM_OPERATOR,
            NOW.minusSeconds(3600),
            NOW.plusSeconds(60),
            Set.of(PRICING_API_LATENCY),
            Set.of(),
            Map.of("endpoint_group", "quote"));

    PerformanceDashboardService.PerformanceDashboardPanel panel = service.panelData(
        request,
        PRICING_API_LATENCY,
        List.of(
            metric(PRICING_API_LATENCY, "pricing.quote.latency.p99", FRESH, false,
                Map.of("endpoint_group", "quote")),
            metric(PRICING_API_LATENCY, "pricing.admin.latency.p99", FRESH, false,
                Map.of("endpoint_group", "admin"))),
        List.of());

    assertEquals(PRICING_API_LATENCY, panel.panel());
    assertEquals(1, panel.metrics().size());
    assertEquals("pricing.quote.latency.p99", panel.metrics().get(0).metricName());
    assertTrue(panel.metrics().get(0).tenantLabel().startsWith("tenant-hash-"));
    assertFalse(panel.metrics().get(0).tenantLabel().contains(TENANT.toString()));
  }

  @Test
  void deniesCrossTenantTenantAdminRead() {
    PerformanceDashboardService.PerformanceDashboardRequest request = new PerformanceDashboardService
        .PerformanceDashboardRequest(
            OTHER_TENANT,
            TENANT,
            TENANT_ADMIN,
            null,
            null,
            Set.of(),
            Set.of(),
            Map.of());

    SecurityException error = assertThrows(SecurityException.class,
        () -> service.summarize(request, List.of(), List.of()));
    assertEquals("TENANT_ACCESS_DENIED", error.getMessage());
  }

  @Test
  void rejectsRawPiiInSnapshotsAlertsAndRunbooks() {
    assertThrows(IllegalArgumentException.class, () -> metric(
        PRICING_API_LATENCY,
        "borrower latency label",
        FRESH,
        false,
        Map.of("endpoint_group", "quote")));

    assertThrows(IllegalArgumentException.class, () -> new PerformanceDashboardService
        .PerformanceAlertSnapshot(
            UUID.randomUUID(),
            TENANT,
            ALERTS_RUNBOOKS,
            CRITICAL,
            "redis-outage-active",
            ACTIVE,
            "customer name Jane Doe impacted",
            "redis-outage",
            NOW.minusSeconds(60),
            null,
            "corr-redis-1"));
  }

  @Test
  void migrationCreatesTenantScopedReadModelsAndIndexes() throws IOException {
    String migration = Files.readString(Path.of(
        "src/main/resources/db/migration/V6__pii17_s10_performance_dashboard.sql"));

    assertTrue(migration.contains("owner_story: PII-17-S10"));
    assertTrue(migration.contains("create table if not exists performance_metric_snapshot"));
    assertTrue(migration.contains("create table if not exists performance_alert_snapshot"));
    assertTrue(migration.contains("on performance_metric_snapshot (tenant_id, panel, window_end desc)"));
    assertTrue(migration.contains("using gin (dimensions_jsonb)"));
    assertTrue(migration.contains("on performance_alert_snapshot (tenant_id, status, severity, started_at desc)"));
  }

  private PerformanceDashboardService.PerformanceDashboardRequest tenantRequest(
      Set<PerformanceDashboardService.DashboardPanel> panels,
      Map<String, String> filters) {
    return new PerformanceDashboardService.PerformanceDashboardRequest(
        TENANT,
        TENANT,
        TENANT_ADMIN,
        NOW.minusSeconds(3600),
        NOW.plusSeconds(60),
        panels,
        Set.of(),
        filters);
  }

  private PerformanceDashboardService.PerformanceMetricSnapshot metric(
      PerformanceDashboardService.DashboardPanel panel,
      String metricName,
      PerformanceDashboardService.FreshnessStatus freshness,
      boolean partial,
      Map<String, String> dimensions) {
    return new PerformanceDashboardService.PerformanceMetricSnapshot(
        UUID.randomUUID(),
        TENANT,
        panel,
        metricName,
        dimensions,
        BigDecimal.valueOf(42),
        null,
        NOW.minusSeconds(300),
        NOW,
        freshness,
        "service-local-fixture",
        partial,
        NOW);
  }

  private PerformanceDashboardService.PerformanceMetricSnapshot otherTenantMetric() {
    return new PerformanceDashboardService.PerformanceMetricSnapshot(
        UUID.randomUUID(),
        OTHER_TENANT,
        PRICING_API_LATENCY,
        "pricing.quote.latency.p95",
        Map.of("endpoint_group", "quote"),
        BigDecimal.ONE,
        null,
        NOW.minusSeconds(300),
        NOW,
        FRESH,
        "service-local-fixture",
        false,
        NOW);
  }

  private PerformanceDashboardService.PerformanceAlertSnapshot alert() {
    return new PerformanceDashboardService.PerformanceAlertSnapshot(
        UUID.randomUUID(),
        TENANT,
        ALERTS_RUNBOOKS,
        CRITICAL,
        "redis-outage-active",
        ACTIVE,
        "Redis outage active for pricing cache namespace",
        "redis-outage",
        NOW.minusSeconds(120),
        null,
        "corr-redis-1");
  }
}
