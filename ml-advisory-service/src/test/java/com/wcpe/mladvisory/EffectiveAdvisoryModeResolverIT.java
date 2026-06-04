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
}
