package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MlAdvisoryControlControllerContractTest {
  private static final String TENANT = "11111111-1111-1111-1111-111111111111";

  @Test
  void shouldExposeStoryEndpointContracts() {
    assertEquals(
        "POST /api/v1/tenants/{tenantId}/ml-advisory/controls",
        MlAdvisoryControlService.CREATE_CONTROL_ENDPOINT);
    assertEquals(
        "POST /api/v1/tenants/{tenantId}/ml-advisory/kill-switch:activate",
        MlAdvisoryControlService.ACTIVATE_KILL_SWITCH_ENDPOINT);
    assertEquals(
        "POST /api/v1/tenants/{tenantId}/ml-advisory/kill-switch:deactivate",
        MlAdvisoryControlService.DEACTIVATE_KILL_SWITCH_ENDPOINT);
  }

  @Test
  void shouldReturnMachineReadableScopeConflict() {
    MlAdvisoryControlService service =
        new MlAdvisoryControlService(Clock.fixed(Instant.parse("2026-06-04T01:00:00Z"), ZoneOffset.UTC));
    service.createControl(command("idem-1"));

    MlAdvisoryResult<MlAdvisoryControlResponse> conflict = service.createControl(command("idem-2"));

    assertTrue(conflict.errorCode().isPresent());
    assertEquals("ML_SCOPE_CONFLICT", conflict.errorCode().orElseThrow());
  }

  private CreateControlCommand command(String idempotencyKey) {
    return new CreateControlCommand(
        TENANT,
        idempotencyKey,
        "ml-admin-1",
        Set.of(MlAdvisoryControlService.ADMIN_ROLE),
        "retail",
        "conventional",
        AdvisoryType.PRICING,
        AdvisoryMode.SHADOW_ONLY,
        Instant.parse("2026-06-04T00:00:00Z"),
        null,
        "Enable shadow mode",
        "MRM-2026-0001",
        "REGISTERED",
        null,
        null,
        "corr-PII-14-S01");
  }
}
