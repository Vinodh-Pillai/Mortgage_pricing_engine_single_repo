package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.margin.LoCompensationService.AuditRecord;
import com.wcpe.margin.LoCompensationService.CommandReceipt;
import com.wcpe.margin.LoCompensationService.CompAssignmentChangedEvent;
import com.wcpe.margin.LoCompensationService.CompBasis;
import com.wcpe.margin.LoCompensationService.CompCalculationResult;
import com.wcpe.margin.LoCompensationService.CompException;
import com.wcpe.margin.LoCompensationService.CompLedgerStep;
import com.wcpe.margin.LoCompensationService.CompPlanPublishedEvent;
import com.wcpe.margin.LoCompensationService.CompPlanStatus;
import com.wcpe.margin.LoCompensationService.CompensationAssignment;
import com.wcpe.margin.LoCompensationService.CompensationRule;
import com.wcpe.margin.LoCompensationService.ConfigResolver;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoCompensationServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final LoCompensationService service = MarginServiceTestStores.loCompensationService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void appliesConfiguredBasisAndBounds() {
    CommandReceipt created = createDraft("idem-bounds", rule("cfg.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps"));

    CompCalculationResult result = service.simulateQuote("tenant-a", created.planId(), 1, CompBasis.PRICE_POINTS,
        "cfg.loCompBps", new BigDecimal("99.750"), Map.of(), resolver(Map.of(
            "cfg.loCompBps", new BigDecimal("38"),
            "cfg.loCompCapBps", new BigDecimal("50"),
            "cfg.loCompFloorBps", new BigDecimal("20"))));

    assertAmount("38", result.rawAmount());
    assertAmount("38", result.boundedAmount());
    assertAmount("3800", result.priceImpactBps());
    assertEquals(new BigDecimal("61.750"), result.priceAfterComp());
    assertEquals(1, result.steps().size());
    CompLedgerStep step = result.steps().get(0);
    assertAmount("38", step.amount());
    assertFalse(step.capFloorApplied());
    assertEquals(new BigDecimal("99.750"), step.inputPrice());
    assertEquals(new BigDecimal("61.750"), step.outputPrice());
    assertEquals("LO_COMP", step.reasonCode());
  }

  @Test
  void clampsRawCompToCap() {
    CommandReceipt created = createDraft("idem-cap", rule("cfg.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps"));

    CompCalculationResult result = service.simulateQuote("tenant-a", created.planId(), 1, CompBasis.PRICE_POINTS,
        "cfg.loCompBps", new BigDecimal("99.750"), Map.of(), resolver(Map.of(
            "cfg.loCompBps", new BigDecimal("72.5"),
            "cfg.loCompCapBps", new BigDecimal("55"),
            "cfg.loCompFloorBps", new BigDecimal("20"))));

    assertAmount("72.5", result.rawAmount());
    assertAmount("55.0", result.boundedAmount());
    assertAmount("5500", result.priceImpactBps());
    assertTrue(result.steps().get(0).capFloorApplied());
    assertAmount("55", result.steps().get(0).amount());
  }

  @Test
  void clampsRawCompToFloor() {
    CommandReceipt created = createDraft("idem-floor", rule("cfg.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps"));

    CompCalculationResult result = service.simulateQuote("tenant-a", created.planId(), 1, CompBasis.PRICE_POINTS,
        "cfg.loCompBps", new BigDecimal("99.750"), Map.of(), resolver(Map.of(
            "cfg.loCompBps", new BigDecimal("10"),
            "cfg.loCompCapBps", new BigDecimal("55"),
            "cfg.loCompFloorBps", new BigDecimal("20"))));

    assertAmount("10", result.rawAmount());
    assertAmount("20", result.boundedAmount());
    assertTrue(result.steps().get(0).capFloorApplied());
    assertAmount("20", result.steps().get(0).amount());
  }

  @Test
  void rejectsCapBelowFloor() {
    CommandReceipt created = service.createDraftPlan("tenant-a", "request-1", "creator-a", "idem-invalid-bounds", "corr-create",
        "Retail LO Comp", 1, List.of(rule("cfg.loCompBps", "cfg.lowCap", "cfg.highFloor")));

    CompException exception = assertThrows(CompException.class,
        () -> service.simulateQuote("tenant-a", created.planId(), 1, CompBasis.PRICE_POINTS,
            "cfg.loCompBps", new BigDecimal("99.750"), Map.of(), resolver(Map.of(
                "cfg.loCompBps", new BigDecimal("38"),
                "cfg.lowCap", new BigDecimal("10"),
                "cfg.highFloor", new BigDecimal("20")))));

    assertEquals("COMP_CAP_FLOOR_INVALID", exception.getMessage());
  }

  @Test
  void rejectsOverlappingAssignment() {
    CommandReceipt published = publish("idem-overlap", "creator-a", "approver-b");
    String versionId = ((LoCompensationService.CompPlanPublishedEvent) published.events().get(0)).versionId();

    CommandReceipt assigned = service.createAssignment("tenant-a", published.planId(), "ops-a", "idem-assignment-1", "corr-assign-1", 1,
        assignment("assignment-1", versionId, "lo-a", "branch-1", "retail", "conventional",
            NOW, NOW.plusSeconds(3600)));

    assertEquals(1, assigned.events().size());
    CompAssignmentChangedEvent assignmentEvent = (CompAssignmentChangedEvent) assigned.events().get(0);
    assertEquals("tenant-a", assignmentEvent.tenantId());
    assertEquals("assignment-1", assignmentEvent.assignmentId());
    assertEquals(versionId, assignmentEvent.planVersionId());
    assertEquals(LoCompensationService.LO_PAYEE_TYPE, assignmentEvent.payeeType());
    assertEquals("lo-a", assignmentEvent.payeeId());
    assertEquals("ops-a", assignmentEvent.actorId());
    assertEquals("corr-assign-1", assignmentEvent.correlationId());
    assertEquals(2, service.outboxEvents().size());
    assertTrue(service.outboxEvents().contains(assignmentEvent));
    assertTrue(service.auditRecords().stream().map(AuditRecord::action).toList()
        .contains("LO_COMP_ASSIGNMENT_CREATED"));

    CompException exception = assertThrows(CompException.class,
        () -> service.createAssignment("tenant-a", published.planId(), "ops-a", "idem-assignment-2", "corr-assign-2", 1,
            assignment("assignment-2", versionId, "lo-a", "branch-1", "retail", "conventional",
                NOW.plusSeconds(60), NOW.plusSeconds(7200))));

    assertEquals("COMP_ASSIGNMENT_OVERLAP", exception.getMessage());
    assertEquals(1, service.compAssignmentOverlapRejectedTotal.get());
  }

  @Test
  void rejectsAssignmentForNonPublishedPlan() {
    CommandReceipt created = createDraft("idem-non-published", rule("cfg.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps"));

    CompException exception = assertThrows(CompException.class,
        () -> service.createAssignment("tenant-a", created.planId(), "ops-a", "idem-draft-assignment", "corr-assign", 1,
            assignment("assignment-1", "version-1", "lo-a", "branch-1", "retail", "conventional",
                NOW, NOW.plusSeconds(3600))));

    assertEquals("COMP_VERSION_NOT_PUBLISHED", exception.getMessage());
  }

  @Test
  void redactsSensitiveFields() {
    CompCalculationResult sensitive = resultWithVisibility("SENSITIVE");

    CompCalculationResult redacted = service.applyVisibility("pricing.comp.lo.view_public", sensitive);

    assertEquals(sensitive.planId(), redacted.planId());
    assertNull(redacted.rawAmount());
    assertNull(redacted.floorAmount());
    assertNull(redacted.capAmount());
    assertNull(redacted.boundedAmount());
    assertNull(redacted.priceImpactBps());
    assertNull(redacted.priceAfterComp());
    assertEquals(1, service.compSensitiveViewDeniedTotal.get());
  }

  @Test
  void showsPublicFieldsWithoutRedaction() {
    CompCalculationResult visible = resultWithVisibility("PUBLIC");

    CompCalculationResult result = service.applyVisibility("pricing.comp.lo.view_public", visible);

    assertAmount("38", result.rawAmount());
    assertAmount("38", result.boundedAmount());
    assertEquals(new BigDecimal("61.750"), result.priceAfterComp());
    assertEquals(0, service.compSensitiveViewDeniedTotal.get());
  }

  @Test
  void planLifecycleWithSoD() {
    CommandReceipt created = createDraft("idem-lifecycle", rule("cfg.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps"));

    CommandReceipt submitted = service.submitForApproval("tenant-a", created.planId(), "creator-a", "corr-submit");
    assertEquals(CompPlanStatus.PENDING_APPROVAL, submitted.status());
    assertEquals("COMP_APPROVAL_SOD_VIOLATION", assertThrows(CompException.class,
        () -> service.complianceApprove("tenant-a", created.planId(), "creator-a", "corr-self-approve")).getMessage());

    CommandReceipt approved = service.complianceApprove("tenant-a", created.planId(), "approver-b", "corr-approve");
    CommandReceipt published = service.publish("tenant-a", created.planId(), "publisher-c", "corr-publish");

    assertEquals(CompPlanStatus.APPROVED, approved.status());
    assertEquals(CompPlanStatus.PUBLISHED, published.status());
    assertEquals(1, service.outboxEvents().size());
    CompPlanPublishedEvent event = (LoCompensationService.CompPlanPublishedEvent) service.outboxEvents().get(0);
    assertEquals("tenant-a", event.tenantId());
    assertEquals(created.planId(), event.planId());
    assertEquals("publisher-c", event.actorId());
    assertEquals("corr-publish", event.correlationId());
    assertTrue(service.auditRecords().stream().map(AuditRecord::action).toList().containsAll(List.of(
        "LO_COMP_DRAFT_CREATED", "LO_COMP_SUBMITTED", "LO_COMP_APPROVED", "LO_COMP_PUBLISHED")));
  }

  @Test
  void rejectsSelfApproval() {
    CommandReceipt created = createDraft("idem-self-approval", rule("cfg.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps"));
    service.submitForApproval("tenant-a", created.planId(), "creator-a", "corr-submit");

    CompException exception = assertThrows(CompException.class,
        () -> service.complianceApprove("tenant-a", created.planId(), "creator-a", "corr-approve"));

    assertEquals("COMP_APPROVAL_SOD_VIOLATION", exception.getMessage());
  }

  @Test
  void idempotencyReplayAndConflict() {
    CompensationRule rule = rule("cfg.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps");
    CommandReceipt first = service.createDraftPlan("tenant-a", "request-1", "creator-a", "idem-replay",
        "corr-create", "Retail LO Comp", 1, List.of(rule));

    CommandReceipt replay = service.createDraftPlan("tenant-a", "request-1", "creator-a", "idem-replay",
        "corr-create", "Retail LO Comp", 1, List.of(rule));

    assertEquals(first, replay);
    assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(CompException.class,
        () -> service.createDraftPlan("tenant-a", "request-1", "creator-a", "idem-replay",
            "corr-create", "Retail LO Comp Changed", 1, List.of(rule))).getMessage());
  }

  @Test
  void resolveActiveAssignmentReturnsCorrectLO() {
    CommandReceipt published = publish("idem-resolve", "creator-a", "approver-b");
    String versionId = ((LoCompensationService.CompPlanPublishedEvent) published.events().get(0)).versionId();
    service.createAssignment("tenant-a", published.planId(), "ops-a", "idem-lo-a", "corr-lo-a", 1,
        assignment("assignment-lo-a", versionId, "lo-a", "branch-1", "retail", "conventional",
            NOW.minusSeconds(60), NOW.plusSeconds(3600)));
    service.createAssignment("tenant-a", published.planId(), "ops-a", "idem-lo-b", "corr-lo-b", 1,
        assignment("assignment-lo-b", versionId, "lo-b", "branch-1", "retail", "conventional",
            NOW.minusSeconds(60), NOW.plusSeconds(3600)));

    Optional<CompensationAssignment> resolved = service.resolveActiveAssignment("tenant-a", "lo-a", "branch-1",
        "retail", "conventional", NOW.plusSeconds(120));

    assertTrue(resolved.isPresent());
    assertEquals("assignment-lo-a", resolved.get().assignmentId());
    assertEquals("lo-a", resolved.get().payeeId());
    assertTrue(service.resolveActiveAssignment("tenant-a", "lo-missing", "branch-1",
        "retail", "conventional", NOW.plusSeconds(120)).isEmpty());
  }

  @Test
  void missingConfigResolvesFailClosed() {
    CommandReceipt created = createDraft("idem-missing-config", rule("cfg.missingLoCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps"));

    CompException exception = assertThrows(CompException.class,
        () -> service.simulateQuote("tenant-a", created.planId(), 1, CompBasis.PRICE_POINTS,
            "cfg.missingLoCompBps", new BigDecimal("99.750"), Map.of(), ref -> Optional.empty()));

    assertEquals("POLICY_NOT_SATISFIED", exception.getMessage());
  }

  @Test
  void prohibitedBasisFailsClosed() {
    CommandReceipt created = createDraft("idem-prohibited-basis", rule("prohibited.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps"));

    CompException exception = assertThrows(CompException.class,
        () -> service.simulateQuote("tenant-a", created.planId(), 1, CompBasis.PRICE_POINTS,
            "prohibited.loCompBps", new BigDecimal("99.750"), Map.of(), ref -> Optional.empty()));

    assertEquals("COMP_BASIS_INVALID", exception.getMessage());
  }

  private CommandReceipt createDraft(String idempotencyKey, CompensationRule rule) {
    return service.createDraftPlan("tenant-a", "request-1", "creator-a", idempotencyKey, "corr-create",
        "Retail LO Comp", 1, List.of(rule));
  }

  private CommandReceipt publish(String idempotencyKey, String creator, String approver) {
    CommandReceipt created = service.createDraftPlan("tenant-a", "request-1", creator, idempotencyKey, "corr-create",
        "Retail LO Comp", 1, List.of(rule("cfg.loCompBps", "cfg.loCompCapBps", "cfg.loCompFloorBps")));
    service.submitForApproval("tenant-a", created.planId(), creator, "corr-submit");
    service.complianceApprove("tenant-a", created.planId(), approver, "corr-approve");
    return service.publish("tenant-a", created.planId(), "publisher-c", "corr-publish");
  }

  private static CompensationRule rule(String amountRef, String capRef, String floorRef) {
    return new CompensationRule("rule-1", "version-1", CompBasis.PRICE_POINTS, amountRef,
        Map.of("ref", amountRef), capRef, floorRef, null, null, "LO_COMP", "SENSITIVE", 1);
  }

  private static CompensationAssignment assignment(String assignmentId, String versionId, String payeeId,
      String branchId, String channel, String productFamily, Instant effectiveFrom, Instant effectiveTo) {
    return new CompensationAssignment(assignmentId, versionId, LoCompensationService.LO_PAYEE_TYPE, payeeId, branchScope(branchId), channelScope(channel),
        productScope(productFamily), effectiveFrom, effectiveTo);
  }

  private static Map<String, Object> branchScope(String branchId) {
    return Map.of("branchId", branchId);
  }

  private static Map<String, Object> channelScope(String channel) {
    return Map.of("channel", channel);
  }

  private static Map<String, Object> productScope(String productFamily) {
    return Map.of("productFamily", productFamily);
  }

  private static ConfigResolver resolver(Map<String, BigDecimal> values) {
    return ref -> Optional.ofNullable(values.get(ref));
  }

  private static CompCalculationResult resultWithVisibility(String visibilityClassification) {
    return new CompCalculationResult("plan-1", "version-1", CompBasis.PRICE_POINTS, new BigDecimal("38"),
        new BigDecimal("20"), new BigDecimal("50"), new BigDecimal("38"), new BigDecimal("3800"),
        new BigDecimal("61.750"), List.of(new CompLedgerStep("LO_COMP", new BigDecimal("99.750"),
            new BigDecimal("38"), CompBasis.PRICE_POINTS, false, new BigDecimal("50"), new BigDecimal("20"),
            new BigDecimal("61.750"), visibilityClassification, "replay-hash-1")), "lo-a");
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
