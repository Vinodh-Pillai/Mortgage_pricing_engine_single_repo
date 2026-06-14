package com.wcpe.lock.nonqm;

import static com.wcpe.lock.nonqm.NonQmLockModels.*;
import static org.junit.jupiter.api.Assertions.*;

import com.wcpe.lock.BusinessDayCalculator;
import com.wcpe.lock.LockServiceException;
import com.wcpe.lock.TenantCalendarClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NonQmLockServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("45454545-4545-4545-4545-454545454545");

  private NonQmLockService service;

  @BeforeEach
  void setUp() {
    service = new NonQmLockService(new BusinessDayCalculator(TenantCalendarClient.configuredLocalDefault()));
    service.importDeliveryProfile(activeDeliveryProfile("PROFILE-INV-A-PRIVATE-POOL", "INV-A"));
    service.importPolicy(policy("POLICY-INV-A-DSCR", "INV-A", NonQmProductType.DSCR, 2, activeDeliveryProfile("PROFILE-INV-A-PRIVATE-POOL", "INV-A")));
  }

  @Test
  void nonQmLockSelectsInvestorProductPolicyExtendedTermCalendarAndSecondaryProfile() {
    NonQmLockDecision decision = service.requestLock(lockRequest("REQ-LOCK-001", "IDEMP-LOCK-001", waterfall("INV-A", NonQmProductType.DSCR), 90));

    NonQmLockRecord detail = service.getLock(TENANT_ID, decision.lockId());

    assertEquals("POLICY-INV-A-DSCR", decision.policyCode());
    assertEquals(90, decision.termDays());
    assertEquals(DeliveryType.PRIVATE_POOL, decision.deliveryType());
    assertEquals("WATERFALL-QUOTE-001", detail.pricingWaterfallRef());
    assertEquals(90, detail.expirationBreakdown().businessDaysAdded());
    assertTrue(detail.expirationBreakdown().weekendsExcluded() > 0);
    assertEquals("non_qm.lock_requested.v1", decision.outboxEventType());
  }

  @Test
  void missingNonQmPolicyFailsClosedWithPolicyCode() {
    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.requestLock(lockRequest("REQ-LOCK-002", "IDEMP-LOCK-002", waterfall("INV-MISSING", NonQmProductType.DSCR), 90))
    );

    assertEquals("LOCK_POLICY_MISSING", error.code());
  }

  @Test
  void changedPricingWaterfallRequiresRepriceOrOverrideArtifact() {
    NonQmLockRequest stale = new NonQmLockRequest(
      TENANT_ID, "REQ-LOCK-003", "lock-desk-1", waterfall("INV-A", NonQmProductType.DSCR), 90,
      Instant.parse("2026-06-01T17:00:00Z"), "OLD-WATERFALL-HASH", "", Map.of(), "IDEMP-LOCK-003", "CORR-LOCK-003"
    );

    LockServiceException error = assertThrows(LockServiceException.class, () -> service.requestLock(stale));

    assertEquals("REPRICE_REQUIRED", error.code());
  }

  @Test
  void constructionExtensionMissingEvidenceCreatesPendingRequestWithFeeRule() {
    NonQmLockDecision lock = service.requestLock(lockRequest("REQ-LOCK-004", "IDEMP-LOCK-004", waterfall("INV-A", NonQmProductType.DSCR), 90));

    NonQmExtensionDecision extension = service.requestExtension(new NonQmExtensionRequest(
      TENANT_ID, lock.lockId(), "REQ-EXT-004", "lock-desk-1", ExtensionType.CONSTRUCTION_DRAW, 10,
      Map.of("projectPhase", "draw-2"), List.of("DRAW_INSPECTION:inspection-123"), Instant.parse("2026-08-01T17:00:00Z"),
      "IDEMP-EXT-004", "CORR-EXT-004"
    ));

    assertEquals(ExtensionStatus.PENDING_EVIDENCE, extension.status());
    assertEquals(List.of("TITLE_UPDATE"), extension.missingEvidenceTypes());
    assertEquals("FEE-RULE-CONSTRUCTION-DRAW", extension.feeResult().feeRuleRef());
    assertEquals(new BigDecimal("12.5"), extension.feeResult().priceImpactBps());
    assertEquals("non_qm.lock_extension_requested.v1", extension.outboxEventType());
  }

  @Test
  void approvedExtensionUpdatesExpirationUsingBusinessDayCalendar() {
    NonQmLockDecision lock = service.requestLock(lockRequest("REQ-LOCK-005", "IDEMP-LOCK-005", waterfall("INV-A", NonQmProductType.DSCR), 60));
    Instant previousExpiration = service.getLock(TENANT_ID, lock.lockId()).expiresAt();

    NonQmExtensionDecision extension = service.requestExtension(new NonQmExtensionRequest(
      TENANT_ID, lock.lockId(), "REQ-EXT-005", "lock-desk-1", ExtensionType.CONSTRUCTION_DRAW, 10,
      Map.of("projectPhase", "draw-2"), List.of("DRAW_INSPECTION:inspection-123", "TITLE_UPDATE:title-123"),
      Instant.parse("2026-08-01T17:00:00Z"), "IDEMP-EXT-005", "CORR-EXT-005"
    ));

    NonQmLockRecord updated = service.getLock(TENANT_ID, lock.lockId());

    assertEquals(ExtensionStatus.APPROVED, extension.status());
    assertTrue(updated.expiresAt().isAfter(previousExpiration));
    assertEquals(1, updated.extensionCount());
    assertEquals(10, extension.requestedExtensionBusinessDays());
  }

  @Test
  void fixFlipExtensionLimitRejectsWithoutInventedFallbackTerms() {
    service.importDeliveryProfile(activeDeliveryProfile("PROFILE-INV-B-FLOW", "INV-B"));
    service.importPolicy(policy("POLICY-INV-B-FIXFLIP", "INV-B", NonQmProductType.FIX_FLIP, 0, activeDeliveryProfile("PROFILE-INV-B-FLOW", "INV-B")));
    NonQmLockDecision lock = service.requestLock(lockRequest("REQ-LOCK-006", "IDEMP-LOCK-006", waterfall("INV-B", NonQmProductType.FIX_FLIP), 90));

    NonQmExtensionDecision extension = service.requestExtension(new NonQmExtensionRequest(
      TENANT_ID, lock.lockId(), "REQ-EXT-006", "lock-desk-1", ExtensionType.FIX_FLIP_TERM_EXTENSION, 15,
      Map.of("exitStrategy", "sale"), List.of("REHAB_STATUS:complete"), Instant.parse("2026-08-01T17:00:00Z"),
      "IDEMP-EXT-006", "CORR-EXT-006"
    ));

    assertEquals(ExtensionStatus.REJECTED, extension.status());
    assertEquals(List.of("EXTENSION_LIMIT_EXCEEDED"), extension.reasonCodes());
  }

  @Test
  void floatDownUsesConfiguredNonQmRuleAndPricingEvidence() {
    NonQmLockDecision lock = service.requestLock(lockRequest("REQ-LOCK-007", "IDEMP-LOCK-007", waterfall("INV-A", NonQmProductType.DSCR), 90));

    FloatDownDecision rejected = service.requestFloatDown(new FloatDownRequest(
      TENANT_ID, lock.lockId(), "lock-desk-1", new BigDecimal("10.0"), "PRICE-EVIDENCE-FLOAT-001",
      Instant.parse("2026-06-15T17:00:00Z"), "IDEMP-FLOAT-007A", "CORR-FLOAT-007A"
    ));
    FloatDownDecision approved = service.requestFloatDown(new FloatDownRequest(
      TENANT_ID, lock.lockId(), "lock-desk-1", new BigDecimal("30.0"), "PRICE-EVIDENCE-FLOAT-002",
      Instant.parse("2026-06-16T17:00:00Z"), "IDEMP-FLOAT-007B", "CORR-FLOAT-007B"
    ));

    assertEquals(FloatDownStatus.REJECTED, rejected.status());
    assertEquals(List.of("MINIMUM_IMPROVEMENT_NOT_MET"), rejected.reasonCodes());
    assertEquals(FloatDownStatus.APPROVED, approved.status());
    assertEquals("FLOAT-RULE-INV-A-DSCR", approved.ruleRef());
    assertEquals("PRICE-EVIDENCE-FLOAT-002", approved.pricingEvidenceRef());
  }

  @Test
  void secondaryDeliveryPackageAndCommitmentLetterExposeGuidelinesPoolingAndPricingEvidence() {
    NonQmLockDecision lock = service.requestLock(lockRequest("REQ-LOCK-008", "IDEMP-LOCK-008", waterfall("INV-A", NonQmProductType.DSCR), 90));

    SecondaryDeliveryPackage delivery = service.secondaryDeliveryPackage(TENANT_ID, lock.lockId());
    LockCommitmentLetter letter = service.commitmentLetter(TENANT_ID, lock.lockId());

    assertEquals("PROFILE-INV-A-PRIVATE-POOL", delivery.profileCode());
    assertTrue(delivery.guidelineRefs().contains("GUIDE-INV-A-NONQM"));
    assertTrue(delivery.poolingAttributes().contains("PRIVATE_POOL_NONQM"));
    assertEquals("WATERFALL-QUOTE-001", delivery.exportFields().get("pricingWaterfallRef"));
    assertEquals("WATERFALL-QUOTE-001", letter.pricingWaterfallRef());
    assertTrue(letter.borrowerVisibleAssumptions().stream().anyMatch(value -> value.contains("configured investor Non-QM policy")));
  }

  @Test
  void apiContractConstantsCoverStoryEndpoints() {
    assertEquals("/api/v1/locks/non-qm/request", NonQmLockApi.POST_LOCK_REQUEST_PATH);
    assertEquals("/api/v1/locks/non-qm/{lockId}/extensions", NonQmLockApi.POST_EXTENSION_PATH);
    assertEquals("/api/v1/locks/non-qm/{lockId}", NonQmLockApi.GET_LOCK_DETAIL_PATH);
    assertEquals("/api/v1/secondary/non-qm/delivery-profiles/{profileCode}", NonQmLockApi.GET_DELIVERY_PROFILE_PATH);
    assertEquals("/api/v1/secondary/non-qm/delivery-profiles/import", NonQmLockApi.POST_DELIVERY_PROFILE_IMPORT_PATH);
  }

  private static SecondaryDeliveryProfile activeDeliveryProfile(String profileCode, String investorCode) {
    return new SecondaryDeliveryProfile(
      profileCode, investorCode, profileCode.contains("FLOW") ? DeliveryType.FLOW : DeliveryType.PRIVATE_POOL,
      List.of("GUIDE-" + investorCode + "-NONQM", "OPTIMAL-BLUE-CUSTOM-FIELDS"),
      List.of("PRIVATE_POOL_NONQM", "DSCR_BUCKET"), List.of("CONDITIONS-PII-41-CLEAR"),
      Map.of("pollyProfile", profileCode, "loanPassWorkflow", "NONQM_LOCK_SECONDARY"), "ACTIVE"
    );
  }

  private static NonQmLockPolicy policy(String policyCode, String investorCode, NonQmProductType productType, int maxFixFlipExtensions, SecondaryDeliveryProfile profile) {
    return new NonQmLockPolicy(
      policyCode, investorCode, "RETAIL", productType,
      List.of(
        new LockTermOption("NONQM-60", 60, new BigDecimal("7.5"), false, "FLOAT-NOT-AVAILABLE-60", BigDecimal.ZERO, List.of("INVESTOR-LOCK-CONDITION")),
        new LockTermOption("NONQM-90", 90, new BigDecimal("17.5"), true, "FLOAT-RULE-" + investorCode + "-" + productType.name(), new BigDecimal("25.0"), List.of("INVESTOR-EXTENDED-LOCK-CONDITION"))
      ),
      List.of(
        new ExtensionPolicy("EXT-CONSTRUCTION-DRAW", ExtensionType.CONSTRUCTION_DRAW, 2, 30, "FEE-RULE-CONSTRUCTION-DRAW", "APPROVAL-RULE-DRAW-EVIDENCE", new BigDecimal("12.5"), List.of("DRAW_INSPECTION", "TITLE_UPDATE")),
        new ExtensionPolicy("EXT-FIX-FLIP", ExtensionType.FIX_FLIP_TERM_EXTENSION, maxFixFlipExtensions, 20, "FEE-RULE-FIX-FLIP", "APPROVAL-RULE-REHAB-EVIDENCE", new BigDecimal("18.0"), List.of("REHAB_STATUS"))
      ),
      profile, 1, "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"), null
    );
  }

  private static PricingWaterfallSnapshot waterfall(String investorCode, NonQmProductType productType) {
    return new PricingWaterfallSnapshot(
      "QUOTE-001", investorCode, "RETAIL", productType, "WATERFALL-QUOTE-001", "HASH-QUOTE-001",
      Instant.parse("2026-06-01T16:00:00Z"), Map.of("priceStack", "PII-40:waterfall:QUOTE-001")
    );
  }

  private static NonQmLockRequest lockRequest(String requestId, String idempotencyKey, PricingWaterfallSnapshot waterfall, int termDays) {
    return new NonQmLockRequest(
      TENANT_ID, requestId, "lock-desk-1", waterfall, termDays, Instant.parse("2026-06-01T17:00:00Z"),
      waterfall.auditHash(), "", Map.of("purpose", "business"), idempotencyKey, "CORR-" + requestId
    );
  }
}
