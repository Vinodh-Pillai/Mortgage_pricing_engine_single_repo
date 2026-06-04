package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MlAdvisoryControlPolicyTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final Instant EFFECTIVE_FROM = Instant.parse("2026-06-04T00:00:00Z");

  private final MlAdvisoryControlService service =
      new MlAdvisoryControlService(Clock.fixed(Instant.parse("2026-06-04T01:00:00Z"), ZoneOffset.UTC));

  @Test
  void shouldDefaultToDisabledWhenNoFlagExists() {
    MlAdvisoryControlResponse response =
        service.resolveEffectiveMode(query(TENANT_ONE)).value().orElseThrow();

    assertEquals(AdvisoryMode.DISABLED, response.configuredMode());
    assertEquals(AdvisoryMode.DISABLED, response.effectiveMode());
    assertFalse(response.killSwitchActive());
  }

  @Test
  void shouldRejectVisibleModeForUnapprovedModelVersion() {
    MlAdvisoryResult<MlAdvisoryControlResponse> result =
        service.createControl(command(TENANT_ONE, AdvisoryMode.ADVISORY_VISIBLE, "REGISTERED", "idem-1"));

    assertFalse(result.valid());
    assertEquals("ML_MODEL_NOT_APPROVED", result.errorCode().orElseThrow());
    assertTrue(service.outboxEvents().isEmpty());
    assertTrue(service.auditRecords().isEmpty());
  }

  @Test
  void shouldCreateApprovedVisibleFlagWithAuditOutboxAndCacheKey() {
    MlAdvisoryControlResponse response =
        service
            .createControl(command(TENANT_ONE, AdvisoryMode.ADVISORY_VISIBLE, "APPROVED_FOR_ADVISORY", "idem-1"))
            .value()
            .orElseThrow();

    assertEquals(AdvisoryMode.ADVISORY_VISIBLE, response.configuredMode());
    assertEquals(AdvisoryMode.ADVISORY_VISIBLE, service.resolveEffectiveMode(query(TENANT_ONE)).value().orElseThrow().effectiveMode());
    assertTrue(response.cacheKey().startsWith("tenant:" + TENANT_ONE + ":ml-advisory:control:"));
    assertEquals(MlAdvisoryControlService.CONTROL_CHANGED_EVENT, service.outboxEvents().get(0).eventType());
    assertEquals("ML_ADVISORY_CONTROL_CHANGED", service.auditRecords().get(0).action());
  }

  @Test
  void shouldReplayIdempotentCreateAndRejectConflictingReplay() {
    MlAdvisoryControlResponse first =
        service.createControl(command(TENANT_ONE, AdvisoryMode.SHADOW_ONLY, "REGISTERED", "idem-1")).value().orElseThrow();

    MlAdvisoryControlResponse replay =
        service.createControl(command(TENANT_ONE, AdvisoryMode.SHADOW_ONLY, "REGISTERED", "idem-1")).value().orElseThrow();
    MlAdvisoryResult<MlAdvisoryControlResponse> conflict =
        service.createControl(command(TENANT_ONE, AdvisoryMode.DISABLED, "REGISTERED", "idem-1"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.errorCode().orElseThrow());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void shouldNotReadOtherTenantControl() {
    service.createControl(command(TENANT_ONE, AdvisoryMode.SHADOW_ONLY, "REGISTERED", "idem-1"));

    assertEquals(AdvisoryMode.SHADOW_ONLY, service.resolveEffectiveMode(query(TENANT_ONE)).value().orElseThrow().effectiveMode());
    assertEquals(AdvisoryMode.DISABLED, service.resolveEffectiveMode(query(TENANT_TWO)).value().orElseThrow().effectiveMode());
  }

  private CreateControlCommand command(String tenantId, AdvisoryMode mode, String modelStatus, String idempotencyKey) {
    return new CreateControlCommand(
        tenantId,
        idempotencyKey,
        "ml-admin-1",
        Set.of(MlAdvisoryControlService.ADMIN_ROLE),
        "retail",
        "conventional",
        AdvisoryType.PRICING,
        mode,
        EFFECTIVE_FROM,
        null,
        "Governance-approved advisory control change",
        "MRM-2026-0001",
        modelStatus,
        mode == AdvisoryMode.ADVISORY_VISIBLE ? "model-risk-admin-2" : null,
        mode == AdvisoryMode.ADVISORY_VISIBLE ? "APPROVAL-PII-14-S01" : null,
        "corr-PII-14-S01");
  }

  private ResolveControlQuery query(String tenantId) {
    return new ResolveControlQuery(tenantId, "retail", "conventional", AdvisoryType.PRICING, "corr-PII-14-S01");
  }
}
