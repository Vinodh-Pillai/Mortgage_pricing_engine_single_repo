package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.IntegrationHealthDashboardService.ActionLink;
import com.wcpe.integration.IntegrationHealthDashboardService.ComponentHealthResponse;
import com.wcpe.integration.IntegrationHealthDashboardService.HealthResult;
import com.wcpe.integration.IntegrationHealthDashboardService.HealthStatus;
import com.wcpe.integration.IntegrationHealthDashboardService.IntegrationComponentType;
import com.wcpe.integration.IntegrationHealthDashboardService.IntegrationHealthSummary;
import com.wcpe.integration.IntegrationHealthDashboardService.RecordHealthSnapshotCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationHealthDashboardServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";

  @Test
  void recordsTenantSnapshotAndReturnsSummaryWithoutPayloadFields() {
    IntegrationHealthDashboardService service = service();

    HealthResult<IntegrationHealthDashboardService.IntegrationHealthSnapshot> recorded =
        service.recordSnapshot(
            new RecordHealthSnapshotCommand(
                TENANT_ONE,
                "snapshot-1",
                List.of(
                    IntegrationHealthDashboardService.component(
                        IntegrationComponentType.CHANNEL_API,
                        HealthStatus.HEALTHY,
                        Instant.parse("2026-06-07T00:59:00Z"),
                        null,
                        3,
                        0,
                        0,
                        0,
                        false,
                        Map.of("requestCount", "42", "rawPayload", "borrower-secret"),
                        List.of(new ActionLink("Channel requests", "/ops/integrations/channel-api?tenant=" + TENANT_ONE))),
                    IntegrationHealthDashboardService.component(
                        IntegrationComponentType.WEBHOOK_DELIVERY,
                        HealthStatus.DEGRADED,
                        Instant.parse("2026-06-07T00:58:00Z"),
                        Instant.parse("2026-06-07T00:58:30Z"),
                        45,
                        2,
                        1,
                        0,
                        true,
                        Map.of("payloadHash", "abc", "failureClass", "RETRYABLE_5XX"),
                        List.of(new ActionLink("Webhook DLQ", "/ops/integrations/webhooks/dlq?tenant=" + TENANT_ONE)))),
                Instant.parse("2026-06-07T01:00:00Z"),
                "ops-admin",
                "corr-PII-16-S08"));

    assertTrue(recorded.valid());
    IntegrationHealthSummary summary = service.summary(TENANT_ONE, "corr-read").value().orElseThrow();
    assertEquals(HealthStatus.DEGRADED, summary.rollupStatus());
    assertEquals(7, summary.componentCount());
    assertEquals(1, summary.degradedComponentCount());
    assertEquals(1, summary.sloBreachCount());
    assertEquals(60, summary.snapshotAgeSeconds());

    List<ComponentHealthResponse> components = service.components(TENANT_ONE);
    ComponentHealthResponse webhook = component(components, IntegrationComponentType.WEBHOOK_DELIVERY);
    assertEquals("<redacted>", component(components, IntegrationComponentType.CHANNEL_API).summary().get("rawPayload"));
    assertEquals("<redacted>", webhook.summary().get("payloadHash"));
    assertFalse(components.toString().contains("borrower-secret"));
    assertEquals(HealthStatus.UNKNOWN, component(components, IntegrationComponentType.DLQ).status());
    assertEquals(IntegrationHealthDashboardService.HEALTH_STATUS_CHANGED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
  }

  @Test
  void enforcesTenantIsolationAndValidation() {
    IntegrationHealthDashboardService service = service();

    assertEquals("VALIDATION_FAILED", service.recordSnapshot(new RecordHealthSnapshotCommand(TENANT_ONE, "", List.of(), null, "ops-admin", "corr")).error().orElseThrow().reason());
    assertEquals("NOT_FOUND", service.summary(TENANT_TWO, "corr-other").error().orElseThrow().reason());

    recordHealthySnapshot(service, TENANT_ONE, "snapshot-1", "corr-1");
    assertTrue(service.summary(TENANT_ONE, "corr-read").valid());
    assertTrue(service.components(TENANT_TWO).isEmpty());
    assertTrue(service.incidents(TENANT_TWO).isEmpty());
    assertTrue(service.metrics(TENANT_TWO, IntegrationComponentType.CHANNEL_API).isEmpty());
  }

  @Test
  void derivesIncidentsMetricsAndStatusChangedEventsFromSnapshots() {
    IntegrationHealthDashboardService service = service();
    recordHealthySnapshot(service, TENANT_ONE, "snapshot-1", "corr-1");
    service.recordSnapshot(
        new RecordHealthSnapshotCommand(
            TENANT_ONE,
            "snapshot-2",
            List.of(
                IntegrationHealthDashboardService.component(
                    IntegrationComponentType.SFTP_FEEDS,
                    HealthStatus.DOWN,
                    Instant.parse("2026-06-07T00:55:00Z"),
                    Instant.parse("2026-06-07T01:00:00Z"),
                    300,
                    4,
                    0,
                    0,
                    true,
                    Map.of("runStatus", "FAILED"),
                    List.of(new ActionLink("SFTP feed runs", "/ops/integrations/sftp/runs?tenant=" + TENANT_ONE)))),
            Instant.parse("2026-06-07T01:00:30Z"),
            "ops-admin",
            "corr-2"));

    assertEquals(HealthStatus.DOWN, service.summary(TENANT_ONE, "corr-read").value().orElseThrow().rollupStatus());
    assertEquals(1, service.incidents(TENANT_ONE).size());
    assertEquals(2, service.metrics(TENANT_ONE, IntegrationComponentType.SFTP_FEEDS).size());
    assertEquals(2, service.outboxEvents().size());
    assertEquals(2L, service.dashboardMetrics().get("integration_health_snapshots_total"));
  }

  private void recordHealthySnapshot(IntegrationHealthDashboardService service, String tenantId, String snapshotId, String correlationId) {
    service.recordSnapshot(
        new RecordHealthSnapshotCommand(
            tenantId,
            snapshotId,
            List.of(
                IntegrationHealthDashboardService.component(
                    IntegrationComponentType.CHANNEL_API,
                    HealthStatus.HEALTHY,
                    Instant.parse("2026-06-07T00:58:00Z"),
                    null,
                    1,
                    0,
                    0,
                    0,
                    false,
                    Map.of("requestCount", "3"),
                    List.of()),
                IntegrationHealthDashboardService.component(
                    IntegrationComponentType.SFTP_FEEDS,
                    HealthStatus.HEALTHY,
                    Instant.parse("2026-06-07T00:57:00Z"),
                    null,
                    2,
                    0,
                    0,
                    0,
                    false,
                    Map.of("feedRuns", "1"),
                    List.of())),
            Instant.parse("2026-06-07T00:59:00Z"),
            "ops-admin",
            correlationId));
  }

  private ComponentHealthResponse component(List<ComponentHealthResponse> components, IntegrationComponentType componentType) {
    return components.stream().filter(component -> component.componentType() == componentType).findFirst().orElseThrow();
  }

  private IntegrationHealthDashboardService service() {
    return new IntegrationHealthDashboardService(Clock.fixed(Instant.parse("2026-06-07T01:01:00Z"), ZoneOffset.UTC));
  }
}
