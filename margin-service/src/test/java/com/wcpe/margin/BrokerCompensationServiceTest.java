package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.margin.BrokerCompensationService.BrokerCompException;
import com.wcpe.margin.BrokerCompensationService.BrokerCompensationAssignment;
import com.wcpe.margin.BrokerCompensationService.BrokerCompensationAssignmentChangedEvent;
import com.wcpe.margin.BrokerCompensationService.BrokerCompensationPlanPublishedEvent;
import com.wcpe.margin.BrokerCompensationService.BrokerCompensationResult;
import com.wcpe.margin.BrokerCompensationService.BrokerCompensationRule;
import com.wcpe.margin.BrokerCompensationService.CommandReceipt;
import com.wcpe.margin.BrokerCompensationService.CompBasis;
import com.wcpe.margin.BrokerCompensationService.ConfigResolver;
import com.wcpe.margin.BrokerCompensationService.PaymentResponsibility;
import com.wcpe.margin.BrokerCompensationService.PlanStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BrokerCompensationServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final BrokerCompensationService service = new BrokerCompensationService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void handlesLenderPaidConfiguredBasis() {
    CommandReceipt published = publish("idem-lender-paid", PaymentResponsibility.LENDER_PAID,
        "cfg.brokerAmountRef", "cfg.brokerCapRef", "cfg.brokerFloorRef", "SENSITIVE");
    String versionId = ((BrokerCompensationPlanPublishedEvent) published.events().get(0)).versionId();
    assign(published.planId(), versionId, "broker-a", "wholesale", NOW.minusSeconds(60), NOW.plusSeconds(3600));

    BrokerCompensationResult result = service.simulateQuote("tenant-a", published.planId(), 1, "broker-a",
        "wholesale", PaymentResponsibility.LENDER_PAID, new BigDecimal("99.750"), Map.of(), resolver(Map.of(
            "cfg.brokerAmountRef", new BigDecimal("0.375"),
            "cfg.brokerCapRef", new BigDecimal("0.500"),
            "cfg.brokerFloorRef", new BigDecimal("0.125"))));

    assertAmount("0.375", result.rawAmount());
    assertAmount("0.375", result.boundedAmount());
    assertAmount("37.5", result.priceImpactBps());
    assertEquals(new BigDecimal("99.375"), result.priceAfterBrokerComp());
    assertEquals(PaymentResponsibility.LENDER_PAID, result.paymentResponsibility());
    assertEquals("BROKER_COMP", result.steps().get(0).reasonCode());
    assertEquals(1, service.brokerCompResolveTotal.get());
  }

  @Test
  void keepsBorrowerPaidPriceAndDisclosureLabel() {
    CommandReceipt published = publish("idem-borrower-paid", PaymentResponsibility.BORROWER_PAID,
        "cfg.borrowerPaidBrokerAmountRef", "cfg.borrowerPaidBrokerCapRef", "cfg.borrowerPaidBrokerFloorRef", "AGGREGATE");
    String versionId = ((BrokerCompensationPlanPublishedEvent) published.events().get(0)).versionId();
    assign(published.planId(), versionId, "broker-a", "wholesale", NOW.minusSeconds(60), NOW.plusSeconds(3600));

    BrokerCompensationResult result = service.simulateQuote("tenant-a", published.planId(), 1, "broker-a",
        "wholesale", PaymentResponsibility.BORROWER_PAID, new BigDecimal("99.750"), Map.of(), resolver(Map.of(
            "cfg.borrowerPaidBrokerAmountRef", new BigDecimal("0.250"),
            "cfg.borrowerPaidBrokerCapRef", new BigDecimal("0.500"),
            "cfg.borrowerPaidBrokerFloorRef", new BigDecimal("0.125"))));

    assertAmount("0.250", result.boundedAmount());
    assertEquals(new BigDecimal("99.750"), result.priceAfterBrokerComp());
    assertEquals("Configured broker compensation", result.disclosureLabel());
  }

  @Test
  void rejectsMissingPaymentResponsibility() {
    BrokerCompException exception = assertThrows(BrokerCompException.class,
        () -> service.createDraftPlan("tenant-a", "request-1", "creator-a", "idem-missing-responsibility",
            "corr-create", "Wholesale Broker Comp", 1, List.of(new BrokerCompensationRule("rule-1",
                CompBasis.PRICE_POINTS, null, "cfg.brokerAmountRef", "cfg.brokerCapRef", "cfg.brokerFloorRef",
                "BROKER_COMP", "SENSITIVE", "Configured broker compensation", Set.of("wholesale"), 1))));

    assertEquals("paymentResponsibility is required", exception.getMessage());
  }

  @Test
  void failsOnOverlaps() {
    CommandReceipt published = publish("idem-overlap", PaymentResponsibility.LENDER_PAID,
        "cfg.brokerAmountRef", "cfg.brokerCapRef", "cfg.brokerFloorRef", "SENSITIVE");
    String versionId = ((BrokerCompensationPlanPublishedEvent) published.events().get(0)).versionId();
    assign(published.planId(), versionId, "broker-a", "wholesale", NOW, NOW.plusSeconds(3600));

    BrokerCompException exception = assertThrows(BrokerCompException.class,
        () -> assign(published.planId(), versionId, "broker-a", "wholesale", NOW.plusSeconds(60), NOW.plusSeconds(7200)));

    assertEquals("BROKER_ASSIGNMENT_OVERLAP", exception.getMessage());
    assertEquals(1, service.brokerAssignmentOverlapRejectedTotal.get());
  }

  @Test
  void publishWholesalePlan() {
    CommandReceipt created = createDraft("idem-publish", PaymentResponsibility.LENDER_PAID,
        "cfg.brokerAmountRef", "cfg.brokerCapRef", "cfg.brokerFloorRef", "SENSITIVE");

    CommandReceipt submitted = service.submitForApproval("tenant-a", created.planId(), "creator-a", "corr-submit");
    assertEquals(PlanStatus.PENDING_APPROVAL, submitted.status());
    assertEquals("BROKER_COMP_APPROVAL_SOD_VIOLATION", assertThrows(BrokerCompException.class,
        () -> service.complianceApprove("tenant-a", created.planId(), "creator-a", "corr-self-approve")).getMessage());
    CommandReceipt approved = service.complianceApprove("tenant-a", created.planId(), "approver-b", "corr-approve");
    CommandReceipt published = service.publish("tenant-a", created.planId(), "publisher-c", "corr-publish");

    assertEquals(PlanStatus.APPROVED, approved.status());
    assertEquals(PlanStatus.PUBLISHED, published.status());
    assertEquals(1, service.outboxEvents().size());
    BrokerCompensationPlanPublishedEvent event = (BrokerCompensationPlanPublishedEvent) service.outboxEvents().get(0);
    assertEquals("tenant-a", event.tenantId());
    assertEquals(created.planId(), event.planId());
    assertEquals("publisher-c", event.actorId());
    assertEquals("corr-publish", event.correlationId());
    assertTrue(service.auditRecords().stream().map(BrokerCompensationService.AuditRecord::action).toList()
        .contains("BROKER_COMPENSATION_PLAN_PUBLISHED"));
  }

  @Test
  void assignmentChangedV1() {
    CommandReceipt published = publish("idem-assignment-event", PaymentResponsibility.LENDER_PAID,
        "cfg.brokerAmountRef", "cfg.brokerCapRef", "cfg.brokerFloorRef", "SENSITIVE");
    String versionId = ((BrokerCompensationPlanPublishedEvent) published.events().get(0)).versionId();

    CommandReceipt assigned = assign(published.planId(), versionId, "broker-a", "wholesale", NOW, NOW.plusSeconds(3600));

    assertEquals(1, assigned.events().size());
    BrokerCompensationAssignmentChangedEvent event = (BrokerCompensationAssignmentChangedEvent) assigned.events().get(0);
    assertEquals("tenant-a", event.tenantId());
    assertEquals(BrokerCompensationService.BROKER_PAYEE_TYPE, event.payeeType());
    assertEquals("broker-a", event.payeeId());
    assertEquals("wholesale", event.channel());
    assertTrue(service.outboxEvents().contains(event));
  }

  @Test
  void noCrossTenantBrokerRead() {
    CommandReceipt published = publish("idem-tenant-isolation", PaymentResponsibility.LENDER_PAID,
        "cfg.brokerAmountRef", "cfg.brokerCapRef", "cfg.brokerFloorRef", "SENSITIVE");
    String versionId = ((BrokerCompensationPlanPublishedEvent) published.events().get(0)).versionId();
    assign(published.planId(), versionId, "broker-a", "wholesale", NOW, NOW.plusSeconds(3600));

    assertTrue(service.resolveActiveAssignment("tenant-b", "broker-a", "wholesale", NOW.plusSeconds(120)).isEmpty());
  }

  @Test
  void visibilityPreviewMasksInternalValues() {
    CommandReceipt published = publish("idem-visibility", PaymentResponsibility.LENDER_PAID,
        "cfg.brokerAmountRef", "cfg.brokerCapRef", "cfg.brokerFloorRef", "SENSITIVE");
    String versionId = ((BrokerCompensationPlanPublishedEvent) published.events().get(0)).versionId();
    assign(published.planId(), versionId, "broker-a", "wholesale", NOW.minusSeconds(60), NOW.plusSeconds(3600));
    BrokerCompensationResult internal = service.simulateQuote("tenant-a", published.planId(), 1, "broker-a",
        "wholesale", PaymentResponsibility.LENDER_PAID, new BigDecimal("99.750"), Map.of(), resolver(Map.of(
            "cfg.brokerAmountRef", new BigDecimal("0.375"),
            "cfg.brokerCapRef", new BigDecimal("0.500"),
            "cfg.brokerFloorRef", new BigDecimal("0.125"))));

    BrokerCompensationResult redacted = service.applyVisibility("pricing.comp.broker.view_public", internal);

    assertNull(redacted.rawAmount());
    assertNull(redacted.boundedAmount());
    assertNull(redacted.priceImpactBps());
    assertEquals(new BigDecimal("99.375"), redacted.priceAfterBrokerComp());
    assertEquals("Configured broker compensation", redacted.disclosureLabel());
    assertEquals(1, service.brokerCompVisibilityRedactionTotal.get());
  }

  @Test
  void missingPlanConfigurationFailsClosed() {
    CommandReceipt published = publish("idem-missing-config", PaymentResponsibility.LENDER_PAID,
        "cfg.missingBrokerAmountRef", "cfg.brokerCapRef", "cfg.brokerFloorRef", "SENSITIVE");
    String versionId = ((BrokerCompensationPlanPublishedEvent) published.events().get(0)).versionId();
    assign(published.planId(), versionId, "broker-a", "wholesale", NOW.minusSeconds(60), NOW.plusSeconds(3600));

    BrokerCompException exception = assertThrows(BrokerCompException.class,
        () -> service.simulateQuote("tenant-a", published.planId(), 1, "broker-a", "wholesale",
            PaymentResponsibility.LENDER_PAID, new BigDecimal("99.750"), Map.of(), ref -> Optional.empty()));

    assertEquals("POLICY_NOT_SATISFIED", exception.getMessage());
    assertEquals(1, service.brokerCompFailClosedTotal.get());
  }

  private CommandReceipt createDraft(String idempotencyKey, PaymentResponsibility responsibility, String amountRef,
      String capRef, String floorRef, String visibilityClassification) {
    return service.createDraftPlan("tenant-a", "request-1", "creator-a", idempotencyKey, "corr-create",
        "Wholesale Broker Comp", 1, List.of(rule(responsibility, amountRef, capRef, floorRef, visibilityClassification)));
  }

  private CommandReceipt publish(String idempotencyKey, PaymentResponsibility responsibility, String amountRef,
      String capRef, String floorRef, String visibilityClassification) {
    CommandReceipt created = createDraft(idempotencyKey, responsibility, amountRef, capRef, floorRef, visibilityClassification);
    service.submitForApproval("tenant-a", created.planId(), "creator-a", "corr-submit");
    service.complianceApprove("tenant-a", created.planId(), "approver-b", "corr-approve");
    return service.publish("tenant-a", created.planId(), "publisher-c", "corr-publish");
  }

  private CommandReceipt assign(String planId, String versionId, String brokerId, String channel, Instant effectiveFrom,
      Instant effectiveTo) {
    return service.createAssignment("tenant-a", planId, "ops-a", "idem-assignment-" + brokerId + channel + effectiveFrom,
        "corr-assign", 1, new BrokerCompensationAssignment("assignment-" + brokerId + channel + effectiveFrom,
            versionId, BrokerCompensationService.BROKER_PAYEE_TYPE, brokerId, channel, effectiveFrom, effectiveTo));
  }

  private static BrokerCompensationRule rule(PaymentResponsibility responsibility, String amountRef, String capRef,
      String floorRef, String visibilityClassification) {
    return new BrokerCompensationRule("rule-1", CompBasis.PRICE_POINTS, responsibility, amountRef, capRef, floorRef,
        "BROKER_COMP", visibilityClassification, "Configured broker compensation", Set.of("wholesale"), 1);
  }

  private static ConfigResolver resolver(Map<String, BigDecimal> values) {
    return ref -> Optional.ofNullable(values.get(ref));
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
