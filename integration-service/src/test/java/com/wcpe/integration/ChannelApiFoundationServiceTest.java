package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.ChannelApiFoundationService.ChannelResponse;
import com.wcpe.integration.ChannelApiFoundationService.ChannelResult;
import com.wcpe.integration.ChannelApiFoundationService.ChannelClient;
import com.wcpe.integration.ChannelApiFoundationService.ChannelStatus;
import com.wcpe.integration.ChannelApiFoundationService.ChannelType;
import com.wcpe.integration.ChannelApiFoundationService.RegisterChannelClient;
import com.wcpe.integration.ChannelApiFoundationService.UpdateChannelClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelApiFoundationServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";

  private final ChannelApiFoundationService service =
      new ChannelApiFoundationService(Clock.fixed(Instant.parse("2026-06-04T01:00:00Z"), ZoneOffset.UTC));

  @Test
  void registersTenantScopedChannelWithAuditAndOutboxEvidence() {
    ChannelResult<ChannelResponse> result = service.register(registerCommand(TENANT_ONE, "idem-1", "los-main"));

    assertTrue(result.valid());
    ChannelResponse response = result.value().orElseThrow();
    assertEquals(ChannelStatus.DRAFT, response.status());
    assertEquals(1, response.version());
    assertEquals("corr-PII-16-S01", response.correlationId());
    assertEquals(1, service.channelsForTenant(TENANT_ONE).size());
    assertEquals(0, service.channelsForTenant(TENANT_TWO).size());
    assertEquals(ChannelApiFoundationService.REGISTERED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(ChannelApiFoundationService.AUDIT_REGISTERED_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void replaysSameIdempotencyKeyAndRejectsChangedRequest() {
    RegisterChannelClient original = registerCommand(TENANT_ONE, "idem-1", "los-main");
    ChannelResponse first = service.register(original).value().orElseThrow();

    ChannelResponse replay = service.register(original).value().orElseThrow();
    ChannelResult<ChannelResponse> conflict = service.register(registerCommand(TENANT_ONE, "idem-1", "different-ref"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow().reason());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void failsClosedWhenPolicyBackedConfigurationIsMissing() {
    ChannelResult<ChannelResponse> result =
        service.register(
            new RegisterChannelClient(
                TENANT_ONE,
                "idem-1",
                "integration-admin",
                "los-main",
                "Primary LOS",
                ChannelType.LOS,
                List.of(),
                Map.of(),
                Map.of("environment", "test"),
                "corr-PII-16-S01"));

    assertFalse(result.valid());
    assertEquals("POLICY_NOT_SATISFIED", result.error().orElseThrow().reason());
    assertTrue(service.auditRecords().isEmpty());
    assertTrue(service.outboxEvents().isEmpty());
  }

  @Test
  void enforcesTenantIsolationAndVersionedStatusTransitions() {
    ChannelResponse registered = service.register(registerCommand(TENANT_ONE, "idem-1", "los-main")).value().orElseThrow();

    assertFalse(service.fetch(TENANT_TWO, registered.id(), "corr-PII-16-S01").valid());
    assertFalse(
        service
            .update(
                new UpdateChannelClient(
                    TENANT_ONE,
                    registered.id(),
                    "idem-invalid-transition",
                    "integration-admin",
                    ChannelStatus.SUSPENDED,
                    1,
                    List.of("product-ref-conventional"),
                    Map.of("policyRef", "tenant-rate-limit-policy"),
                    Map.of("environment", "test"),
                    "corr-PII-16-S01"))
            .valid());
    assertFalse(service.validateReadiness(TENANT_ONE, registered.id(), "corr-PII-16-S01").ready());

    ChannelResponse activated =
        service
            .update(
                new UpdateChannelClient(
                    TENANT_ONE,
                    registered.id(),
                    "idem-2",
                    "integration-admin",
                    ChannelStatus.ACTIVE,
                    1,
                    List.of("product-ref-conventional"),
                    Map.of("policyRef", "tenant-rate-limit-policy"),
                    Map.of("environment", "test"),
                    "corr-PII-16-S01"))
            .value()
            .orElseThrow();

    assertEquals(ChannelStatus.ACTIVE, activated.status());
    assertEquals(2, activated.version());
    assertEquals(ChannelApiFoundationService.UPDATED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
    assertTrue(service.validateReadiness(TENANT_ONE, registered.id(), "corr-PII-16-S01").ready());
  }

  @Test
  void readinessFailsForMissingProducts() throws Exception {
    putChannel(
        new ChannelClient(
            TENANT_ONE,
            "channel-without-products",
            "los-no-products",
            "LOS without products",
            ChannelType.LOS,
            ChannelStatus.ACTIVE,
            List.of(),
            Map.of("policyRef", "tenant-rate-limit-policy"),
            Map.of("environment", "test"),
            1,
            "integration-admin",
            Instant.parse("2026-06-04T01:00:00Z"),
            Instant.parse("2026-06-04T01:00:00Z"),
            "corr-PII-16-S01"));

    ChannelApiFoundationService.ChannelReadiness readiness =
        service.validateReadiness(TENANT_ONE, "channel-without-products", "corr-PII-16-S01");

    assertFalse(readiness.ready());
    assertEquals("POLICY_NOT_SATISFIED", readiness.validationMessages().get(0).reason());
  }

  @SuppressWarnings("unchecked")
  private void putChannel(ChannelClient channel) throws Exception {
    java.lang.reflect.Field channelsField = ChannelApiFoundationService.class.getDeclaredField("channels");
    channelsField.setAccessible(true);
    Map<String, ChannelClient> channels = (Map<String, ChannelClient>) channelsField.get(service);
    channels.put(channel.tenantId() + ":" + channel.channelId(), channel);
  }

  private RegisterChannelClient registerCommand(String tenantId, String idempotencyKey, String externalRef) {
    return new RegisterChannelClient(
        tenantId,
        idempotencyKey,
        "integration-admin",
        externalRef,
        "Primary LOS",
        ChannelType.LOS,
        List.of("product-ref-conventional"),
        Map.of("policyRef", "tenant-rate-limit-policy"),
        Map.of("environment", "test"),
        "corr-PII-16-S01");
  }
}
