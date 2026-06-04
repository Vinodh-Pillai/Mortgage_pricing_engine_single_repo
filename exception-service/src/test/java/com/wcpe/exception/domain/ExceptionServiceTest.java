package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExceptionServiceTest {

  private ExceptionService service;

  @BeforeEach
  void setUp() {
    service = new ExceptionService(new ExceptionRepository());
  }

  @Test
  void createReturnsMockBackedDraftStatus() {
    ExceptionModels.ExceptionRequestStatus status = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-123", ExceptionModels.ExceptionType.CONCESSION)
    );

    assertEquals("QUOTE-123", status.placeholderQuoteReference());
    assertEquals(ExceptionModels.ExceptionState.DRAFT, status.state());
    assertTrue(status.mockBacked());
    assertFalse(status.authoritativeIntegration());
    assertNotNull(status.exceptionRequestId());
  }

  @Test
  void createRejectsMissingPlaceholderQuoteReferenceWithDeterministicError() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.create(new ExceptionModels.ExceptionRequestCreate(" ", ExceptionModels.ExceptionType.CONCESSION))
    );

    assertEquals("MISSING_PLACEHOLDER_QUOTE_REFERENCE", error.code());
    ExceptionModels.ExceptionError contractError = service.toError(error, null);
    assertEquals("MISSING_PLACEHOLDER_QUOTE_REFERENCE", contractError.code());
    assertEquals("placeholderQuoteReference is required", contractError.message());
  }

  @Test
  void transitionAllowsApprovedLifecyclePath() {
    ExceptionModels.ExceptionRequestStatus created = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-456", ExceptionModels.ExceptionType.EXCEPTION)
    );

    ExceptionModels.ExceptionTransitionResponse submitted = service.transition(
      created.exceptionRequestId(),
      new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.SUBMITTED)
    );
    ExceptionModels.ExceptionTransitionResponse approved = service.transition(
      created.exceptionRequestId(),
      new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.APPROVED)
    );

    assertEquals(ExceptionModels.ExceptionState.DRAFT, submitted.previousState());
    assertEquals(ExceptionModels.ExceptionState.SUBMITTED, submitted.newState());
    assertEquals(ExceptionModels.ExceptionState.SUBMITTED, approved.previousState());
    assertEquals(ExceptionModels.ExceptionState.APPROVED, approved.newState());
    assertTrue(approved.mockBacked());
    assertFalse(approved.authoritativeIntegration());
  }

  @Test
  void transitionRejectsTerminalStateRepeatWithDeterministicError() {
    ExceptionModels.ExceptionRequestStatus created = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-789", ExceptionModels.ExceptionType.CONCESSION)
    );
    service.transition(created.exceptionRequestId(), new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.SUBMITTED));
    service.transition(created.exceptionRequestId(), new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.APPROVED));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.transition(
        created.exceptionRequestId(),
        new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.APPROVED)
      )
    );

    assertEquals("INVALID_TRANSITION", error.code());
    assertTrue(error.getMessage().contains("Cannot transition from APPROVED to APPROVED"));
  }

  @Test
  void statusRejectsUnknownRequestIdWithDeterministicError() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.status("EXC-404")
    );

    assertEquals("UNKNOWN_EXCEPTION_REQUEST", error.code());
    assertEquals("Unknown exception request id: EXC-404", error.getMessage());
  }

  @Test
  void createPricingConcessionSubmitsWithAuditEventAndRouteSnapshot() {
    ExceptionModels.PricingConcessionRequestStatus status = service.createPricingConcession(concessionRequest(
      "IDEMP-001",
      "Need partner concession without borrower@example.com or 123-45-6789 in storage",
      false,
      false
    ));

    assertEquals(ExceptionModels.ConcessionRequestStatus.SUBMITTED, status.status());
    assertEquals("QUOTE-PII11", status.quoteId());
    assertEquals("SCENARIO-PII11", status.scenarioId());
    assertEquals("POLICY-V1", status.concessionPolicyVersionId());
    assertEquals("MATRIX-V1", status.authorityMatrixVersionId());
    assertEquals(List.of("pricing-desk-manager"), status.nextApproverGroups());
    assertEquals("pricing.concession.requested.v1", status.outboxEventType());
    assertTrue(status.auditRef().startsWith("AUDIT-PCR-"));
    assertNotNull(status.requestHash());
    assertTrue(status.commentsRedacted().contains("[REDACTED]"));
    assertFalse(status.commentsRedacted().contains("borrower@example.com"));
    assertFalse(status.commentsRedacted().contains("123-45-6789"));
  }

  @Test
  void createPricingConcessionFailsClosedWhenAuthorityRouteIsAmbiguous() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.createPricingConcession(concessionRequest("IDEMP-002", "clean narrative", true, false))
    );

    assertEquals("AUTHORITY_ROUTE_UNRESOLVED", error.code());
  }

  @Test
  void createPricingConcessionReplaysSameIdempotencyKeyForSameRequest() {
    ExceptionModels.PricingConcessionRequestCreate request = concessionRequest("IDEMP-003", "clean narrative", false, false);

    ExceptionModels.PricingConcessionRequestStatus first = service.createPricingConcession(request);
    ExceptionModels.PricingConcessionRequestStatus replayed = service.createPricingConcession(request);

    assertEquals(first.concessionRequestId(), replayed.concessionRequestId());
    assertEquals(first.requestHash(), replayed.requestHash());
  }

  @Test
  void createPricingConcessionRejectsIdempotencyConflict() {
    service.createPricingConcession(concessionRequest("IDEMP-004", "first narrative", false, false));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.createPricingConcession(concessionRequest("IDEMP-004", "changed narrative", false, false))
    );

    assertEquals("DUPLICATE_IDEMPOTENCY_KEY", error.code());
  }

  @Test
  void pricingConcessionStatusEnforcesTenantIsolation() {
    ExceptionModels.PricingConcessionRequestStatus status = service.createPricingConcession(concessionRequest(
      "IDEMP-005",
      "clean narrative",
      false,
      false
    ));

    assertEquals(status.concessionRequestId(), service.pricingConcessionStatus(
      "11111111-1111-1111-1111-111111111111",
      status.concessionRequestId()
    ).concessionRequestId());

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.pricingConcessionStatus("22222222-2222-2222-2222-222222222222", status.concessionRequestId())
    );
    assertEquals("UNKNOWN_CONCESSION_REQUEST", error.code());
  }

  @Test
  void createPricingConcessionMarksEligibilityExceptionWhenRequired() {
    ExceptionModels.PricingConcessionRequestStatus status = service.createPricingConcession(concessionRequest(
      "IDEMP-006",
      "clean narrative",
      false,
      true
    ));

    assertEquals(ExceptionModels.ConcessionRequestStatus.NEEDS_ELIGIBILITY_EXCEPTION, status.status());
  }

  @Test
  void approveConcessionMovesSubmittedRequestToApprovedPendingApplication() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-007",
      "clean narrative",
      false,
      false
    ));

    ExceptionModels.ConcessionApprovalResponse approval = service.approveConcession(approvalRequest(
      created.concessionRequestId(),
      "APPROVAL-IDEMP-001",
      "pricing-desk-manager",
      "approval-manager-1",
      created.version()
    ));

    assertEquals(ExceptionModels.ConcessionRequestStatus.SUBMITTED, approval.previousStatus());
    assertEquals(ExceptionModels.ConcessionRequestStatus.APPROVED_PENDING_APPLICATION, approval.newStatus());
    assertEquals("approval-route-step-1", approval.completedStep());
    assertEquals("concession.request.approved.v1", approval.outboxEventType());
    assertTrue(approval.auditRef().startsWith("AUDIT-APD-"));
    assertNotNull(approval.eventHash());
    assertEquals(2, approval.aggregateVersion());
    assertEquals(ExceptionModels.ConcessionRequestStatus.APPROVED_PENDING_APPLICATION, service.pricingConcessionStatus(
      "11111111-1111-1111-1111-111111111111",
      created.concessionRequestId()
    ).status());
  }

  @Test
  void approveConcessionRejectsRequesterSelfApproval() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-008",
      "clean narrative",
      false,
      false
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.approveConcession(approvalRequest(
        created.concessionRequestId(),
        "APPROVAL-IDEMP-002",
        "pricing-desk-manager",
        "loan-officer-7",
        created.version()
      ))
    );

    assertEquals("SEPARATION_OF_DUTIES_VIOLATION", error.code());
  }

  @Test
  void approveConcessionRequiresCurrentRouteApproverScope() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-009",
      "clean narrative",
      false,
      false
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.approveConcession(approvalRequest(
        created.concessionRequestId(),
        "APPROVAL-IDEMP-003",
        "branch-user",
        "approval-manager-1",
        created.version()
      ))
    );

    assertEquals("NOT_CURRENT_APPROVER", error.code());
  }

  @Test
  void approveConcessionRejectsStaleExpectedVersion() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-010",
      "clean narrative",
      false,
      false
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.approveConcession(approvalRequest(
        created.concessionRequestId(),
        "APPROVAL-IDEMP-004",
        "pricing-desk-manager",
        "approval-manager-1",
        99
      ))
    );

    assertEquals("STALE_REQUEST_VERSION", error.code());
  }

  @Test
  void approveConcessionRejectsWhenEligibilityExceptionIsRequired() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-011",
      "clean narrative",
      false,
      true
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.approveConcession(approvalRequest(
        created.concessionRequestId(),
        "APPROVAL-IDEMP-005",
        "pricing-desk-manager",
        "approval-manager-1",
        created.version()
      ))
    );

    assertEquals("ELIGIBILITY_EXCEPTION_REQUIRED", error.code());
  }

  @Test
  void approveConcessionReplaysSameIdempotencyKeyForSameDecision() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-012",
      "clean narrative",
      false,
      false
    ));
    ExceptionModels.ApproveConcessionRequest request = approvalRequest(
      created.concessionRequestId(),
      "APPROVAL-IDEMP-006",
      "pricing-desk-manager",
      "approval-manager-1",
      created.version()
    );

    ExceptionModels.ConcessionApprovalResponse first = service.approveConcession(request);
    ExceptionModels.ConcessionApprovalResponse replayed = service.approveConcession(request);

    assertEquals(first.decisionId(), replayed.decisionId());
    assertEquals(first.eventHash(), replayed.eventHash());
  }

  @Test
  void approveConcessionRejectsIdempotencyConflict() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-013",
      "clean narrative",
      false,
      false
    ));
    service.approveConcession(approvalRequest(
      created.concessionRequestId(),
      "APPROVAL-IDEMP-007",
      "pricing-desk-manager",
      "approval-manager-1",
      created.version()
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.approveConcession(approvalRequest(
        created.concessionRequestId(),
        "APPROVAL-IDEMP-007",
        "different-approver-group",
        "approval-manager-1",
        created.version()
      ))
    );

    assertEquals("IDEMPOTENCY_CONFLICT", error.code());
  }

  private static ExceptionModels.PricingConcessionRequestCreate concessionRequest(
    String idempotencyKey,
    String narrative,
    boolean ambiguousRoute,
    boolean eligibilityExceptionRequired
  ) {
    return new ExceptionModels.PricingConcessionRequestCreate(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      "QUOTE-PII11",
      "SCENARIO-PII11",
      null,
      new ExceptionModels.ConcessionAmount(ExceptionModels.ConcessionUnit.BASIS_POINTS, "-12.500", null),
      "RETENTION_REQUEST",
      narrative,
      List.of(new ExceptionModels.ConcessionEvidenceRef("evidence://doc/123", "SUPPORTING_NOTE", "sha256:abc123")),
      "2026-06-30",
      "loan-officer-7",
      idempotencyKey,
      "corr-pii11",
      "POLICY-V1",
      "REASON-V1",
      "quote-hash-v1",
      new ExceptionModels.ApprovalRouteSnapshot(
        "MATRIX-V1",
        List.of("pricing-desk-manager"),
        "PT4H",
        ambiguousRoute
      ),
      eligibilityExceptionRequired
    );
  }

  private static ExceptionModels.ApproveConcessionRequest approvalRequest(
    String concessionRequestId,
    String idempotencyKey,
    String actorRoleRef,
    String actorId,
    int expectedRequestVersion
  ) {
    return new ExceptionModels.ApproveConcessionRequest(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      concessionRequestId,
      "approval-route-step-1",
      ExceptionModels.ApprovalDecisionType.APPROVE,
      "APPROVED_BY_AUTHORITY_MATRIX",
      "Approved with configured authority; borrower@example.com redacted",
      new ExceptionModels.ApprovalConditions("2026-07-31", Map.of("condition", "documented approval evidence")),
      new ExceptionModels.ConflictAttestation(true, "I am not the requester"),
      "MATRIX-V1",
      actorId,
      List.of(actorRoleRef),
      idempotencyKey,
      "corr-approval-pii11",
      expectedRequestVersion
    );
  }
}
