package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MlAdvisoryControlTenantIsolationIT {
  @Test
  void shouldNotReadOtherTenantControl() {
    MlAdvisoryControlService service =
        new MlAdvisoryControlService(Clock.fixed(Instant.parse("2026-06-04T01:00:00Z"), ZoneOffset.UTC));
    service.createControl(command("11111111-1111-1111-1111-111111111111", "idem-tenant-one"));

    MlAdvisoryControlResponse tenantOne =
        service
            .resolveEffectiveMode(
                new ResolveControlQuery(
                    "11111111-1111-1111-1111-111111111111", "retail", "conventional", AdvisoryType.PRICING, "corr-read"))
            .value()
            .orElseThrow();
    MlAdvisoryControlResponse tenantTwo =
        service
            .resolveEffectiveMode(
                new ResolveControlQuery(
                    "22222222-2222-2222-2222-222222222222", "retail", "conventional", AdvisoryType.PRICING, "corr-read"))
            .value()
            .orElseThrow();

    assertEquals(AdvisoryMode.SHADOW_ONLY, tenantOne.effectiveMode());
    assertEquals(AdvisoryMode.DISABLED, tenantTwo.effectiveMode());
  }

  private CreateControlCommand command(String tenantId, String idempotencyKey) {
    return new CreateControlCommand(
        tenantId,
        idempotencyKey,
        "ml-admin-1",
        Set.of(MlAdvisoryControlService.ADMIN_ROLE),
        "retail",
        "conventional",
        AdvisoryType.PRICING,
        AdvisoryMode.SHADOW_ONLY,
        Instant.parse("2026-06-04T00:00:00Z"),
        null,
        "Enable shadow mode for tenant-scoped rollout",
        "MRM-2026-0001",
        "REGISTERED",
        null,
        null,
        "corr-PII-14-S01");
  }
}
