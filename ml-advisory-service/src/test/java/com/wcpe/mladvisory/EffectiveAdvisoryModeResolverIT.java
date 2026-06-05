package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectiveAdvisoryModeResolverIT {
  private static final String TENANT = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER_TENANT = "22222222-2222-2222-2222-222222222222";

  private final MlAdvisoryControlService service =
      new MlAdvisoryControlService(Clock.fixed(Instant.parse("2026-06-04T01:00:00Z"), ZoneOffset.UTC));

  @Test
  void shouldApplyKillSwitchBeforeTenantFlag() {
    service.createControl(
        new CreateControlCommand(
            TENANT,
            "idem-control",
            "ml-admin-1",
            Set.of(MlAdvisoryControlService.ADMIN_ROLE),
            "retail",
            "conventional",
            AdvisoryType.PRICING,
            AdvisoryMode.SHADOW_ONLY,
            Instant.parse("2026-06-04T00:00:00Z"),
            null,
            "Enable shadow capture for controlled tenant rollout",
            "MRM-2026-0001",
            "REGISTERED",
            null,
            null,
            "corr-control"));

    service.activateKillSwitch(
        new KillSwitchCommand(
            TENANT,
            "idem-kill",
            "ml-admin-1",
            Set.of(MlAdvisoryControlService.ADMIN_ROLE),
            "Incident response disables advisory immediately",
            "corr-kill",
            null));

    MlAdvisoryControlResponse response =
        service
            .resolveEffectiveMode(new ResolveControlQuery(TENANT, "retail", "conventional", AdvisoryType.PRICING, "corr-read"))
            .value()
            .orElseThrow();

    assertEquals(AdvisoryMode.DISABLED, response.effectiveMode());
    assertTrue(response.killSwitchActive());
    assertFalse(response.killSwitchReason().isBlank());
    assertEquals(MlAdvisoryControlService.KILL_SWITCH_CHANGED_EVENT, service.outboxEvents().get(1).eventType());
  }

  @Test
  void shouldApplyGlobalKillSwitchBeforeAnyTenantScopedControl() {
    service.createControl(
        new CreateControlCommand(
            TENANT,
            "idem-global-control",
            "ml-admin-1",
            Set.of(MlAdvisoryControlService.ADMIN_ROLE),
            "retail",
            "conventional",
            AdvisoryType.PRICING,
            AdvisoryMode.SHADOW_ONLY,
            Instant.parse("2026-06-04T00:00:00Z"),
            null,
            "Enable shadow capture for controlled tenant rollout",
            "MRM-2026-0001",
            "REGISTERED",
            null,
            null,
            "corr-global-control"));

    KillSwitchState state =
        service
            .activateKillSwitch(
                new KillSwitchCommand(
                    null,
                    "idem-global-kill",
                    "ml-admin-1",
                    Set.of(MlAdvisoryControlService.ADMIN_ROLE),
                    "Platform incident disables advisory globally",
                    "corr-global-kill",
                    null))
            .value()
            .orElseThrow();

    MlAdvisoryControlResponse tenantResponse =
        service
            .resolveEffectiveMode(new ResolveControlQuery(TENANT, "retail", "conventional", AdvisoryType.PRICING, "corr-read"))
            .value()
            .orElseThrow();
    MlAdvisoryControlResponse otherTenantResponse =
        service
            .resolveEffectiveMode(
                new ResolveControlQuery(OTHER_TENANT, "retail", "conventional", AdvisoryType.PRICING, "corr-read-other"))
            .value()
            .orElseThrow();

    assertEquals(MlAdvisoryControlService.GLOBAL_KILL_SWITCH_TENANT_ID, state.tenantId());
    assertEquals(AdvisoryMode.DISABLED, tenantResponse.effectiveMode());
    assertEquals(AdvisoryMode.DISABLED, otherTenantResponse.effectiveMode());
    assertTrue(tenantResponse.killSwitchActive());
    assertTrue(otherTenantResponse.killSwitchActive());
    assertEquals("Platform incident disables advisory globally", tenantResponse.killSwitchReason());
  }
}
