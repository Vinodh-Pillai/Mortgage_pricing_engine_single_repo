package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wcpe.margin.MarginGovernanceService.ApprovalDecision;
import com.wcpe.margin.MarginGovernanceService.ApprovalRoute;
import com.wcpe.margin.MarginGovernanceService.ApprovalStepPolicy;
import com.wcpe.margin.MarginGovernanceService.ChangeRequestCommand;
import com.wcpe.margin.MarginGovernanceService.ChangeStatus;
import com.wcpe.margin.MarginGovernanceService.GovernanceReceipt;
import com.wcpe.margin.MarginGovernanceService.MarginGovernanceException;
import com.wcpe.margin.MarginGovernanceService.RollbackCommand;
import com.wcpe.margin.MarginGovernanceService.SimulationEvidence;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarginGovernanceServiceTest {
  private final MarginGovernanceService service = MarginServiceTestStores.marginGovernanceService(
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void enforcesStateTransitionsAndPublishesOnlyApprovedConfigHash() {
    GovernanceReceipt created = service.createChangeRequest(command("admin-a", "idem-1", "hash-1", standardRoute()));

    service.submit("tenant-a", created.changeId(), "admin-a", "corr-submit", simulation());
    service.approve("tenant-a", created.changeId(), decision("PRICING", "approver-b", "corr-approve"));
    GovernanceReceipt published = service.publish("tenant-a", created.changeId(), "publisher-c", "corr-publish", 7,
        "cfg-hash-1");

    assertEquals(ChangeStatus.PUBLISHED, published.status());
    assertEquals("POST /api/v1/tenants/{tenantId}/margin-governance/change-requests",
        MarginGovernanceService.CHANGE_REQUESTS_API);
    assertEquals("pricing.governance.approve", MarginGovernanceService.APPROVE_PERMISSION);
    assertEquals(3, service.outboxEvents().size());
    assertTrue(service.auditRecords().stream()
        .anyMatch(record -> "MARGIN_GOVERNANCE_CHANGE_PUBLISHED".equals(record.action())));
    assertEquals(1, service.marginGovernanceChangeTotal.get());
  }

  @Test
  void rejectsSelfApprovalMissingSimulationAndStalePublish() {
    GovernanceReceipt created = service.createChangeRequest(command("admin-a", "idem-2", "hash-2", standardRoute()));

    assertEquals("SIMULATION_REQUIRED", assertThrows(MarginGovernanceException.class,
        () -> service.submit("tenant-a", created.changeId(), "admin-a", "corr-submit",
            new SimulationEvidence("sim-hash-1", "other-diff", List.of(), List.of()))).getMessage());

    service.submit("tenant-a", created.changeId(), "admin-a", "corr-submit", simulation());
    assertEquals("SOD_VIOLATION", assertThrows(MarginGovernanceException.class,
        () -> service.approve("tenant-a", created.changeId(), decision("PRICING", "admin-a", "corr-approve")))
        .getMessage());
    service.approve("tenant-a", created.changeId(), decision("PRICING", "approver-b", "corr-approve"));

    assertEquals("CHANGE_REQUEST_STALE", assertThrows(MarginGovernanceException.class,
        () -> service.publish("tenant-a", created.changeId(), "publisher-c", "corr-publish", 8, "cfg-hash-1"))
        .getMessage());
    assertEquals("CHANGE_REQUEST_STALE", assertThrows(MarginGovernanceException.class,
        () -> service.publish("tenant-a", created.changeId(), "publisher-c", "corr-publish", 7, "cfg-hash-other"))
        .getMessage());
  }

  @Test
  void highRiskChangeRequiresComplianceStepAndApprovesInRouteOrder() {
    ApprovalRoute pricingOnly = standardRoute();
    GovernanceReceipt created = service.createChangeRequest(new ChangeRequestCommand("tenant-a", "request-1", "admin-a",
        "idem-3", "corr-create", "COMPANY_MARGIN", "company-margin", "v1", 7, "cfg-hash-1", "diff-hash-1",
        "HIGH", pricingOnly, "hash-3"));

    assertEquals("APPROVAL_ROUTE_MISSING", assertThrows(MarginGovernanceException.class,
        () -> service.submit("tenant-a", created.changeId(), "admin-a", "corr-submit", simulation())).getMessage());

    GovernanceReceipt compliant = service.createChangeRequest(command("admin-a", "idem-4", "hash-4", complianceRoute()));
    service.submit("tenant-a", compliant.changeId(), "admin-a", "corr-submit", simulation());
    assertEquals("APPROVAL_ROUTE_MISSING", assertThrows(MarginGovernanceException.class,
        () -> service.approve("tenant-a", compliant.changeId(), decision("COMPLIANCE", "compliance-b", "corr-c")))
        .getMessage());
    GovernanceReceipt partial = service.approve("tenant-a", compliant.changeId(),
        decision("PRICING", "approver-b", "corr-p"));
    GovernanceReceipt approved = service.approve("tenant-a", compliant.changeId(),
        decision("COMPLIANCE", "compliance-b", "corr-c"));

    assertEquals(ChangeStatus.PARTIALLY_APPROVED, partial.status());
    assertEquals(ChangeStatus.APPROVED, approved.status());
  }

  @Test
  void rollbackCreatesNewGovernedChangeFromPublishedVersion() {
    GovernanceReceipt published = publishApproved("idem-5", "hash-5");

    GovernanceReceipt rollback = service.rollback("tenant-a", published.changeId(), new RollbackCommand("request-rb",
        "publisher-c", "idem-rb", "corr-rb", "v0", "cfg-hash-1", "cfg-hash-0", "diff-rb", "HIGH",
        complianceRoute(), "EMERGENCY_ROLLBACK", "hash-rb"));

    var rollbackChange = service.readChange("tenant-a", rollback.changeId()).orElseThrow();
    assertEquals(ChangeStatus.DRAFT, rollback.status());
    assertTrue(rollbackChange.rollbackReference().isPresent());
    assertEquals(published.changeId(), rollbackChange.rollbackReference().orElseThrow().sourceChangeId());
    assertFalse(rollback.replayHash().isBlank());
  }

  @Test
  void rejectsIdempotencyConflictAndCrossTenantRead() {
    GovernanceReceipt created = service.createChangeRequest(command("admin-a", "idem-6", "hash-6", standardRoute()));

    assertEquals(created.changeId(), service.createChangeRequest(command("admin-a", "idem-6", "hash-6", standardRoute()))
        .changeId());
    assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(MarginGovernanceException.class,
        () -> service.createChangeRequest(command("admin-a", "idem-6", "different", standardRoute()))).getMessage());
    assertTrue(service.readChange("tenant-b", created.changeId()).isEmpty());
  }

  private GovernanceReceipt publishApproved(String idempotencyKey, String requestHash) {
    GovernanceReceipt created = service.createChangeRequest(command("admin-a", idempotencyKey, requestHash, standardRoute()));
    service.submit("tenant-a", created.changeId(), "admin-a", "corr-submit", simulation());
    service.approve("tenant-a", created.changeId(), decision("PRICING", "approver-b", "corr-approve"));
    return service.publish("tenant-a", created.changeId(), "publisher-c", "corr-publish", 7, "cfg-hash-1");
  }

  private static ChangeRequestCommand command(String actorId, String idempotencyKey, String requestHash,
      ApprovalRoute route) {
    return new ChangeRequestCommand("tenant-a", "request-1", actorId, idempotencyKey, "corr-create", "COMPANY_MARGIN",
        "company-margin", "v1", 7, "cfg-hash-1", "diff-hash-1", "MEDIUM", route, requestHash);
  }

  private static SimulationEvidence simulation() {
    return new SimulationEvidence("sim-hash-1", "diff-hash-1", List.of("fixture:company-margin"),
        List.of("evidence:simulation"));
  }

  private static ApprovalDecision decision(String step, String actorId, String correlationId) {
    return new ApprovalDecision(step, actorId, "approved", List.of("evidence:" + step), correlationId);
  }

  private static ApprovalRoute standardRoute() {
    return new ApprovalRoute(List.of(new ApprovalStepPolicy("PRICING", MarginGovernanceService.APPROVE_PERMISSION)));
  }

  private static ApprovalRoute complianceRoute() {
    return new ApprovalRoute(List.of(
        new ApprovalStepPolicy("PRICING", MarginGovernanceService.APPROVE_PERMISSION),
        new ApprovalStepPolicy("COMPLIANCE", MarginGovernanceService.APPROVE_PERMISSION)));
  }
}
