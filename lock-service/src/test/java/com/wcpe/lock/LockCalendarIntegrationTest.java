package com.wcpe.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LockCalendarIntegrationTest {
  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private LockService service;

  @BeforeEach
  void setUp() {
    TenantCalendarClient tenantClient = tenantId -> new TenantCalendarConfig(
      tenantId,
      "America/Los_Angeles",
      WorkingHours.weekdays(LocalTime.of(9, 0), LocalTime.of(17, 0)),
      List.of(LocalDate.parse("2026-07-03")),
      LocalDate.parse("2026-01-01"),
      LocalDate.parse("2026-12-31")
    );
    service = new LockService(new InMemoryLockRepository(), new BusinessDayCalculator(tenantClient));
  }

  @Test
  void lockServiceUsesTenantCalendarConfigForConfirmationAndDetail() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-CAL-001", "IDEMP-CAL-REQ-001"));
    LockModels.LockDecisionResponse approval = service.decideLock(decisionCommand(request.lockId(), request.version(), "IDEMP-CAL-DEC-001"));

    LockModels.LockConfirmationResponse confirmation = service.confirmLock(confirmationCommand(
      approval.lockId(), approval.version(), "IDEMP-CAL-CONF-001", Instant.parse("2026-06-30T23:00:00Z"), 3
    ));
    LockModels.LockDetailResponse detail = service.getLockDetail(TENANT_ID, approval.lockId());

    assertEquals(Instant.parse("2026-07-07T00:00:00Z"), confirmation.lockExpiresAt());
    assertEquals(3, confirmation.lockPeriodBusinessDays());
    assertEquals(List.of(LocalDate.parse("2026-07-03")), confirmation.expirationBreakdown().holidaysExcluded());
    assertFalse(confirmation.calendarConfigHash().isBlank());
    assertEquals(confirmation.calendarConfigHash(), detail.calendarConfigHash());
    assertNotNull(detail.expirationBreakdown());
  }

  @Test
  void extensionUsesSameCalendarLogic() {
    LockModels.LockConfirmationResponse confirmation = createCalendarAwareActiveLock();

    LockModels.LockExtensionResponse extension = service.requestExtension(new LockModels.LockExtensionRequestCommand(
      TENANT_ID,
      confirmation.lockId(),
      "EXT-CAL-001",
      "lock-desk-user-1",
      confirmation.version(),
      2,
      confirmation.lockExpiresAt(),
      confirmation.lockExpiresAt().plusSeconds(86_400),
      "EXTENSION_REASON_CONFIGURED",
      extensionCostSnapshot(),
      true,
      true,
      true,
      true,
      "compliance-extension-evidence-v1",
      "IDEMP-EXT-CAL-001",
      "corr-pii30-s01-ext",
      Map.of("tenantCalendarConfig", "tenant-context-service:/api/tenant/current")
    ));

    assertEquals(Instant.parse("2026-07-10T00:00:00Z"), extension.requestedExpiresAt());
    assertNotNull(extension.expirationBreakdown());
    assertEquals(2, extension.expirationBreakdown().businessDaysAdded());
    assertFalse(extension.calendarConfigHash().isBlank());
  }

  private LockModels.LockConfirmationResponse createCalendarAwareActiveLock() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-CAL-EXT", "IDEMP-CAL-EXT-REQ"));
    LockModels.LockDecisionResponse approval = service.decideLock(decisionCommand(request.lockId(), request.version(), "IDEMP-CAL-EXT-DEC"));
    return service.confirmLock(confirmationCommand(
      approval.lockId(), approval.version(), "IDEMP-CAL-EXT-CONF", Instant.parse("2026-06-30T23:00:00Z"), 3
    ));
  }

  private static LockModels.LockRequestCommand validCommand(String requestId, String idempotencyKey) {
    return new LockModels.LockRequestCommand(
      TENANT_ID, requestId, "loan-officer-7", "QUOTE-" + requestId, "LOAN-PII30", "scenario-hash-v1",
      "pricing-hash-v1", "rate-sheet-v1", "CONVENTIONAL-30Y", "INVESTOR-A", "retail", 30,
      Instant.parse("2026-06-30T22:50:00Z"), Instant.parse("2026-06-30T23:00:00Z"), idempotencyKey,
      "corr-pii30-s01", true, true, true, true, true, true, false, false, false, true, false,
      "lock-policy-v1", "compliance-evidence-v1", Map.of("tenantCalendarConfig", "tenant-context-service:/api/tenant/current")
    );
  }

  private static LockModels.LockDecisionCommand decisionCommand(String lockId, int expectedVersion, String idempotencyKey) {
    return new LockModels.LockDecisionCommand(
      TENANT_ID, lockId, LockModels.LockDecisionType.APPROVE, "lock-desk-user-1", "requester-9", expectedVersion,
      List.of("TENANT_REASON_OK"), "Decision note", "lock-policy-v1", "compliance-evidence-v1", idempotencyKey,
      "corr-pii30-s01", Instant.parse("2026-06-30T23:01:00Z"), true, true, true
    );
  }

  private static LockModels.LockConfirmationCommand confirmationCommand(
    String lockId,
    int expectedVersion,
    String idempotencyKey,
    Instant confirmedAt,
    int businessDays
  ) {
    return new LockModels.LockConfirmationCommand(
      TENANT_ID, lockId, "lock-desk-user-1", expectedVersion, LockModels.LockConfirmationType.INTERNAL, "LOCK-PII30-001",
      false, "INVESTOR-A", "", businessDays, confirmedAt, confirmedAt.plusSeconds(86_400 * businessDays), "CONVENTIONAL-30Y",
      "CONVENTIONAL-30Y", "LOAN-PII30", "LOAN-PII30", "lock-policy-v1", "lock-policy-v1", true, true,
      true, true, "compliance-evidence-v1", idempotencyKey, "corr-pii30-s01",
      Map.of("tenantCalendarConfig", "tenant-context-service:/api/tenant/current")
    );
  }

  private static LockModels.ExtensionCostSnapshot extensionCostSnapshot() {
    return new LockModels.ExtensionCostSnapshot(
      "price-adjustment-config-ref:EXT-2D", "fee-config-ref:EXT-2D", "BORROWER_PAID",
      "policy-rounding-v1", "EXTENSION_REASON_CONFIGURED", "extension-policy-v1"
    );
  }
}
