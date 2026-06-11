package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExceptionServiceTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private ExceptionRepository repository;
  private ExceptionService service;

  @BeforeEach
  void setUp() {
    repository = new ExceptionRepository();
    service = new ExceptionService(repository);
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
  void createEligibilityExceptionRequestSubmitsWithImmutableFindingSnapshotAuditAndEvent() {
    ExceptionModels.PricingConcessionRequestStatus concession = service.createPricingConcession(concessionRequest(
      "IDEMP-ELIG-CONC-001",
      "clean narrative",
      false,
      true
    ));

    ExceptionModels.EligibilityExceptionRequestResponse response = service.createEligibilityExceptionRequest(
      eligibilityExceptionRequest("ELIG-IDEMP-001", concession.concessionRequestId(), true, true, true),
      eligibilityExceptionPolicy(true, true, true, false)
    );

    assertEquals(ExceptionModels.EligibilityExceptionRequestStatus.SUBMITTED, response.status());
    assertEquals("QUOTE-PII11", response.quoteId());
    assertEquals("SCENARIO-PII11", response.scenarioId());
    assertEquals("ELIG-RESULT-001", response.findingRef().eligibilityResultId());
    assertEquals("FINDING-001", response.findingRef().findingId());
    assertEquals("ELIGIBILITY-RULE-CONFIG-REF", response.findingRef().ruleCode());
    assertEquals("eligibility-result-hash-v1", response.originalResultHash());
    assertEquals("ELIGIBILITY-POLICY-V1", response.policyVersionId());
    assertEquals("MATRIX-V1", response.authorityMatrixVersionId());
    assertEquals(concession.concessionRequestId(), response.relatedConcessionRequestId());
    assertEquals("EligibilityExceptionRequestSubmitted.v1", response.outboxEventType());
    assertTrue(response.auditRef().startsWith("AUDIT-EER-"));
    assertNotNull(response.approvalRouteHash());
    assertNotNull(response.eventHash());
    assertEquals(response.exceptionRequestId(), service.eligibilityExceptionRequestStatus(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      response.exceptionRequestId()
    ).exceptionRequestId());
  }

  @Test
  void createEligibilityExceptionRequestRejectsNonExceptionableFinding() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.createEligibilityExceptionRequest(
        eligibilityExceptionRequest("ELIG-IDEMP-002", null, false, true, true),
        eligibilityExceptionPolicy(true, true, true, false)
      )
    );

    assertEquals("FINDING_NOT_EXCEPTIONABLE", error.code());
  }

  @Test
  void createEligibilityExceptionRequestRequiresConfiguredEvidence() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.createEligibilityExceptionRequest(
        eligibilityExceptionRequest("ELIG-IDEMP-003", null, true, true, false),
        eligibilityExceptionPolicy(true, true, true, false)
      )
    );

    assertEquals("REQUIRED_EVIDENCE_MISSING", error.code());
  }

  @Test
  void createEligibilityExceptionRequestFailsClosedWhenRouteUnresolved() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.createEligibilityExceptionRequest(
        eligibilityExceptionRequest("ELIG-IDEMP-004", null, true, true, true),
        eligibilityExceptionPolicy(true, true, true, true)
      )
    );

    assertEquals("AUTHORITY_ROUTE_UNRESOLVED", error.code());
  }

  @Test
  void createEligibilityExceptionRequestReplaysSameIdempotencyKeyForSameRequest() {
    ExceptionModels.CreateEligibilityExceptionRequest request = eligibilityExceptionRequest(
      "ELIG-IDEMP-005",
      null,
      true,
      true,
      true
    );

    ExceptionModels.EligibilityExceptionRequestResponse first = service.createEligibilityExceptionRequest(
      request,
      eligibilityExceptionPolicy(true, true, true, false)
    );
    ExceptionModels.EligibilityExceptionRequestResponse replayed = service.createEligibilityExceptionRequest(
      request,
      eligibilityExceptionPolicy(true, true, true, false)
    );

    assertEquals(first.exceptionRequestId(), replayed.exceptionRequestId());
    assertEquals(first.eventHash(), replayed.eventHash());
  }

  @Test
  void createEligibilityExceptionRequestRejectsIdempotencyConflict() {
    service.createEligibilityExceptionRequest(
      eligibilityExceptionRequest("ELIG-IDEMP-006", null, true, true, true),
      eligibilityExceptionPolicy(true, true, true, false)
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.createEligibilityExceptionRequest(
        eligibilityExceptionRequest("ELIG-IDEMP-006", null, true, false, true),
        eligibilityExceptionPolicy(true, true, true, false)
      )
    );

    assertEquals("DUPLICATE_IDEMPOTENCY_KEY", error.code());
  }

  @Test
  void createEligibilityExceptionRequestEnforcesActiveUniquenessByFindingAndScope() {
    service.createEligibilityExceptionRequest(
      eligibilityExceptionRequest("ELIG-IDEMP-007", null, true, true, true),
      eligibilityExceptionPolicy(true, true, true, false)
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.createEligibilityExceptionRequest(
        eligibilityExceptionRequest("ELIG-IDEMP-008", null, true, true, true),
        eligibilityExceptionPolicy(true, true, true, false)
      )
    );

    assertEquals("DUPLICATE_ACTIVE_EXCEPTION_REQUEST", error.code());
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

  @Test
  void applyApprovedConcessionAppendsDeterministicLedgerAuditAndEvent() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-014",
      "clean narrative",
      false,
      false
    ));
    service.approveConcession(approvalRequest(
      created.concessionRequestId(),
      "APPROVAL-IDEMP-008",
      "pricing-desk-manager",
      "approval-manager-1",
      created.version()
    ));

    ExceptionModels.ConcessionApplicationResponse applied = service.applyApprovedConcession(applyRequest(
      created.concessionRequestId(),
      "APPLY-IDEMP-001",
      2,
      "quote-hash-v1",
      true
    ));

    assertEquals(ExceptionModels.ConcessionRequestStatus.APPLIED, applied.status());
    assertEquals(ExceptionModels.ApplicationTargetType.QUOTE, applied.targetType());
    assertEquals("QUOTE-PII11", applied.quoteId());
    assertTrue(applied.pricingLedgerEntryId().startsWith("LEDGER-"));
    assertEquals("ledger-hash-before-v1", applied.beforePriceHash());
    assertNotEquals(applied.beforePriceHash(), applied.afterPriceHash());
    assertEquals("PRICING-RULE-V1", applied.pricingRuleVersionId());
    assertEquals("POLICY-V1", applied.policyVersionId());
    assertEquals("PRECEDENCE-V1", applied.precedenceConfigVersionId());
    assertEquals("ConcessionAppliedToQuote.v1", applied.outboxEventType());
    assertTrue(applied.auditRef().startsWith("AUDIT-APP-"));
    assertNotNull(applied.replayHash());
    assertEquals(ExceptionModels.ConcessionRequestStatus.APPLIED, service.pricingConcessionStatus(
      "11111111-1111-1111-1111-111111111111",
      created.concessionRequestId()
    ).status());
  }

  @Test
  void applyApprovedConcessionRejectsUnapprovedRequest() {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      "IDEMP-015",
      "clean narrative",
      false,
      false
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.applyApprovedConcession(applyRequest(
        created.concessionRequestId(),
        "APPLY-IDEMP-002",
        created.version(),
        "quote-hash-v1",
        true
      ))
    );

    assertEquals("REQUEST_NOT_APPROVED", error.code());
  }

  @Test
  void applyApprovedConcessionRejectsChangedQuoteHash() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("IDEMP-016", "APPROVAL-IDEMP-009");

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.applyApprovedConcession(applyRequest(
        created.concessionRequestId(),
        "APPLY-IDEMP-003",
        2,
        "quote-hash-changed",
        true
      ))
    );

    assertEquals("QUOTE_HASH_CHANGED", error.code());
  }

  @Test
  void applyApprovedConcessionRejectsUnresolvedEligibilityException() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("IDEMP-017", "APPROVAL-IDEMP-010");

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.applyApprovedConcession(applyRequest(
        created.concessionRequestId(),
        "APPLY-IDEMP-004",
        2,
        "quote-hash-v1",
        false
      ))
    );

    assertEquals("ELIGIBILITY_EXCEPTION_UNRESOLVED", error.code());
  }

  @Test
  void applyApprovedConcessionIsIdempotentForSameTarget() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("IDEMP-018", "APPROVAL-IDEMP-011");
    ExceptionModels.ApplyApprovedConcessionRequest request = applyRequest(
      created.concessionRequestId(),
      "APPLY-IDEMP-005",
      2,
      "quote-hash-v1",
      true
    );

    ExceptionModels.ConcessionApplicationResponse first = service.applyApprovedConcession(request);
    ExceptionModels.ConcessionApplicationResponse replayed = service.applyApprovedConcession(request);

    assertEquals(first.applicationId(), replayed.applicationId());
    assertEquals(first.replayHash(), replayed.replayHash());
  }

  @Test
  void applyApprovedConcessionRejectsSecondApplicationToSameTarget() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("IDEMP-019", "APPROVAL-IDEMP-012");
    service.applyApprovedConcession(applyRequest(
      created.concessionRequestId(),
      "APPLY-IDEMP-006",
      2,
      "quote-hash-v1",
      true
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.applyApprovedConcession(applyRequest(
        created.concessionRequestId(),
        "APPLY-IDEMP-007",
        3,
        "quote-hash-v1",
        true
      ))
    );

    assertEquals("REQUEST_NOT_APPROVED", error.code());
  }

  @Test
  void applyApprovedConcessionFailsClosedWhenPrecedenceConfigMissing() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("IDEMP-020", "APPROVAL-IDEMP-013");
    ExceptionModels.ApplyApprovedConcessionRequest invalid = new ExceptionModels.ApplyApprovedConcessionRequest(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      created.concessionRequestId(),
      new ExceptionModels.ApplicationTarget(
        ExceptionModels.ApplicationTargetType.QUOTE,
        "QUOTE-PII11",
        null,
        "quote-hash-v1",
        null
      ),
      2,
      "quote-hash-v1",
      "ledger-hash-before-v1",
      "PRICING-RULE-V1",
      "POLICY-V1",
      new ExceptionModels.ApplicationPrecedence(" ", 8, "HALF_UP"),
      true,
      "pricing-desk-user-1",
      "APPLY-IDEMP-008",
      "corr-apply-pii11"
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.applyApprovedConcession(invalid)
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
  }

  @Test
  void priceMutationGuardBlocksConfiguredPriceFieldsAndRecordsAuditEvidence() {
    ExceptionModels.PriceMutationGuardResponse response = service.guardPriceMutation(
      manualPriceEditCommand("GUARD-IDEMP-001", null, ledgerExpectation("ledger-hash-before-v1")),
      priceMutationGuardPolicy()
    );

    assertEquals(ExceptionModels.PriceMutationGuardDecision.BLOCKED, response.decision());
    assertEquals(List.of("MANUAL_PRICE_EDIT_FORBIDDEN"), response.validationMessages());
    assertNotNull(response.blockedEvidence());
    assertEquals(List.of("finalPrice", "margin"), response.blockedEvidence().guardedFields());
    assertEquals("ManualPriceEditBlocked.v1", response.blockedEvidence().outboxEventType());
    assertTrue(response.blockedEvidence().auditRef().startsWith("AUDIT-MPE-"));
    assertNotNull(response.blockedEvidence().payloadHash());
    assertNotNull(response.blockedEvidence().eventHash());
    assertEquals(response.blockedEvidence().attemptId(), service.manualPriceEditAttemptStatus(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      response.blockedEvidence().attemptId()
    ).attemptId());
  }

  @Test
  void priceMutationGuardAllowsApprovedLedgerCommandWithCapabilityAndLedgerHash() {
    ExceptionModels.AuthorizedPricingCommandRef commandRef = new ExceptionModels.AuthorizedPricingCommandRef(
      "AppendPricingLedgerAdjustment",
      "CMD-001",
      "workflow-capability://tenant/111/pricing-ledger-adjustment",
      "APPROVED-ADJ-001"
    );

    ExceptionModels.PriceMutationGuardResponse response = service.guardPriceMutation(
      manualPriceEditCommand("GUARD-IDEMP-002", commandRef, ledgerExpectation("ledger-hash-before-v1")),
      priceMutationGuardPolicy()
    );

    assertEquals(ExceptionModels.PriceMutationGuardDecision.ALLOWED, response.decision());
    assertEquals("Configured governed pricing command authorized the guarded mutation", response.resultSummary());
    assertNull(response.blockedEvidence());
    assertNotNull(response.replayHash());
  }

  @Test
  void priceMutationGuardRejectsFinalPricePatchWithoutApprovedAdjustmentReference() {
    ExceptionModels.AuthorizedPricingCommandRef commandRef = new ExceptionModels.AuthorizedPricingCommandRef(
      "AppendPricingLedgerAdjustment",
      "CMD-002",
      "workflow-capability://tenant/111/pricing-ledger-adjustment",
      " "
    );

    ExceptionModels.PriceMutationGuardResponse response = service.guardPriceMutation(
      manualPriceEditCommand("GUARD-IDEMP-003", commandRef, ledgerExpectation("ledger-hash-before-v1")),
      priceMutationGuardPolicy()
    );

    assertEquals(ExceptionModels.PriceMutationGuardDecision.BLOCKED, response.decision());
    assertEquals("MISSING_APPROVED_ADJUSTMENT_REFERENCE", response.blockedEvidence().denialReason());
    assertEquals("manual-price-edit-api", response.blockedEvidence().sourceSurface());
  }

  @Test
  void priceMutationGuardRequiresLedgerHashForAuthorizedPriceMutation() {
    ExceptionModels.AuthorizedPricingCommandRef commandRef = new ExceptionModels.AuthorizedPricingCommandRef(
      "ApplyApprovedConcession",
      "CMD-003",
      "workflow-capability://tenant/111/apply-approved-concession",
      "APPROVED-CONCESSION-001"
    );

    ExceptionModels.PriceMutationGuardResponse response = service.guardPriceMutation(
      manualPriceEditCommand("GUARD-IDEMP-004", commandRef, ledgerExpectation(" ")),
      priceMutationGuardPolicy()
    );

    assertEquals(ExceptionModels.PriceMutationGuardDecision.BLOCKED, response.decision());
    assertEquals("LEDGER_HASH_REQUIRED", response.blockedEvidence().denialReason());
  }

  @Test
  void priceMutationGuardFailsClosedWhenConfiguredPolicyIsMissing() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.guardPriceMutation(manualPriceEditCommand("GUARD-IDEMP-005", null, ledgerExpectation("ledger-hash-before-v1")), null)
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
  }

  @Test
  void priceMutationGuardReplaysBlockedAttemptForSameIdempotencyKey() {
    ExceptionModels.GuardPriceMutationCommand command = manualPriceEditCommand(
      "GUARD-IDEMP-006",
      null,
      ledgerExpectation("ledger-hash-before-v1")
    );

    ExceptionModels.PriceMutationGuardResponse first = service.guardPriceMutation(command, priceMutationGuardPolicy());
    ExceptionModels.PriceMutationGuardResponse replayed = service.guardPriceMutation(command, priceMutationGuardPolicy());

    assertEquals(first.blockedEvidence().attemptId(), replayed.blockedEvidence().attemptId());
    assertEquals(first.replayHash(), replayed.replayHash());
  }

  @Test
  void monitorConcessionPatternRaisesAlertFromConfiguredPolicyVersion() {
    ExceptionModels.ConcessionMonitoringAlertResponse alert = service.monitorConcessionPattern(
      monitoringSignal("MONITOR-IDEMP-001", ExceptionModels.MonitoringSignalType.FREQUENCY, null, null),
      monitoringPolicy(ExceptionModels.MonitoringPolicyStatus.PUBLISHED, ExceptionModels.AlertSeverity.HIGH, 10, true)
    );

    assertEquals(ExceptionModels.AlertStatus.OPEN, alert.status());
    assertEquals(ExceptionModels.AlertSeverity.HIGH, alert.severity());
    assertEquals("concession-frequency-detector", alert.detectorId());
    assertEquals("detector-version-2026-06", alert.detectorVersionId());
    assertEquals(List.of("ConcessionRequestApproved:evt-001"), alert.sourceEventIds());
    assertEquals("monitoring-policy://tenant/111/frequency/v1", alert.evidenceSnapshot().get("policyConfigRef"));
    assertEquals("PT24H", alert.evidenceSnapshot().get("monitoringWindow"));
    assertTrue(alert.auditRef().startsWith("AUDIT-CMA-"));
    assertEquals("ConcessionMonitoringAlertRaised.v1", alert.outboxEventType());
    assertNotNull(alert.evidenceHash());
    assertNotNull(alert.replayHash());
    assertFalse(alert.fairnessCohortSuppressed());
  }

  @Test
  void monitorConcessionPatternFailsClosedWhenPolicyConfigIsMissing() {
    ExceptionModels.MonitoringPolicyVersion missingConfig = new ExceptionModels.MonitoringPolicyVersion(
      "concession-frequency-detector",
      "detector-version-2026-06",
      ExceptionModels.MonitoringPolicyStatus.PUBLISHED,
      " ",
      ExceptionModels.AlertSeverity.HIGH,
      "PT24H",
      List.of("branchRef", "channel"),
      10,
      true
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.monitorConcessionPattern(
        monitoringSignal("MONITOR-IDEMP-002", ExceptionModels.MonitoringSignalType.FREQUENCY, null, null),
        missingConfig
      )
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
  }

  @Test
  void monitorConcessionPatternSuppressesFairnessCohortWhenConfiguredSmallCellPolicyRequiresIt() {
    ExceptionModels.ConcessionMonitoringAlertResponse alert = service.monitorConcessionPattern(
      monitoringSignal("MONITOR-IDEMP-003", ExceptionModels.MonitoringSignalType.FAIRNESS_DISPARITY, "cohort-ref-approved", 4),
      monitoringPolicy(ExceptionModels.MonitoringPolicyStatus.PUBLISHED, ExceptionModels.AlertSeverity.CRITICAL, 10, true)
    );

    assertEquals(ExceptionModels.AlertStatus.SUPPRESSED, alert.status());
    assertTrue(alert.fairnessCohortSuppressed());
    assertNull(alert.fairnessCohortRef());
    assertEquals("4", alert.evidenceSnapshot().get("fairnessCohortCellCount"));
    assertEquals("10", alert.evidenceSnapshot().get("minimumCellSizePolicy"));
  }

  @Test
  void monitorConcessionPatternSuppressesFairnessCohortWhenCellCountIsMissing() {
    ExceptionModels.ConcessionMonitoringAlertResponse alert = service.monitorConcessionPattern(
      monitoringSignal("MONITOR-IDEMP-004", ExceptionModels.MonitoringSignalType.FAIRNESS_DISPARITY, "cohort-ref-approved", null),
      monitoringPolicy(ExceptionModels.MonitoringPolicyStatus.PUBLISHED, ExceptionModels.AlertSeverity.CRITICAL, 10, true)
    );

    assertEquals(ExceptionModels.AlertStatus.SUPPRESSED, alert.status());
    assertTrue(alert.fairnessCohortSuppressed());
    assertNull(alert.fairnessCohortRef());
    assertFalse(alert.evidenceSnapshot().containsKey("fairnessCohortCellCount"));
    assertEquals("10", alert.evidenceSnapshot().get("minimumCellSizePolicy"));
  }

  @Test
  void monitorConcessionPatternRejectsRawPiiInEvidenceDimensions() {
    ExceptionModels.ConcessionMonitoringSignalCommand signal = new ExceptionModels.ConcessionMonitoringSignalCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      "ConcessionRequestApproved:evt-004",
      "PCR-PII11",
      null,
      "APD-PII11",
      ExceptionModels.MonitoringSignalType.FREQUENCY,
      Map.of("branchRef", "BRANCH-12", "borrowerEmail", "borrower@example.com"),
      Map.of("approvalCountBucket", "configured-high"),
      null,
      null,
      "risk-analyst-1",
      "MONITOR-IDEMP-005",
      "corr-monitor-pii11"
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.monitorConcessionPattern(signal, monitoringPolicy(
        ExceptionModels.MonitoringPolicyStatus.PUBLISHED,
        ExceptionModels.AlertSeverity.HIGH,
        10,
        true
      ))
    );

    assertEquals("RAW_PII_NOT_ALLOWED", error.code());
  }

  @Test
  void monitorConcessionPatternReplaysSameIdempotencyKeyForSameSignal() {
    ExceptionModels.ConcessionMonitoringSignalCommand signal = monitoringSignal(
      "MONITOR-IDEMP-006",
      ExceptionModels.MonitoringSignalType.ROUTE_BYPASS,
      null,
      null
    );
    ExceptionModels.MonitoringPolicyVersion policy = monitoringPolicy(
      ExceptionModels.MonitoringPolicyStatus.PUBLISHED,
      ExceptionModels.AlertSeverity.MEDIUM,
      10,
      false
    );

    ExceptionModels.ConcessionMonitoringAlertResponse first = service.monitorConcessionPattern(signal, policy);
    ExceptionModels.ConcessionMonitoringAlertResponse replayed = service.monitorConcessionPattern(signal, policy);

    assertEquals(first.alertId(), replayed.alertId());
    assertEquals(first.replayHash(), replayed.replayHash());
  }

  @Test
  void monitorConcessionPatternRejectsIdempotencyKeyConflictForDifferentSignal() {
    ExceptionModels.MonitoringPolicyVersion policy = monitoringPolicy(
      ExceptionModels.MonitoringPolicyStatus.PUBLISHED,
      ExceptionModels.AlertSeverity.MEDIUM,
      10,
      false
    );

    service.monitorConcessionPattern(
      monitoringSignal("MONITOR-IDEMP-007", ExceptionModels.MonitoringSignalType.ROUTE_BYPASS, null, null),
      policy
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.monitorConcessionPattern(
        monitoringSignal("MONITOR-IDEMP-007", ExceptionModels.MonitoringSignalType.SLA_BREACH, null, null),
        policy
      )
    );

    assertEquals("IDEMPOTENCY_CONFLICT", error.code());
  }

  @Test
  void dispositionMonitoringAlertTransitionsAlertWithAuditEventAndRedactedComment() {
    ExceptionModels.ConcessionMonitoringAlertResponse alert = service.monitorConcessionPattern(
      monitoringSignal("MONITOR-IDEMP-008", ExceptionModels.MonitoringSignalType.SLA_BREACH, null, null),
      monitoringPolicy(ExceptionModels.MonitoringPolicyStatus.PUBLISHED, ExceptionModels.AlertSeverity.MEDIUM, 10, true)
    );

    ExceptionModels.ConcessionAlertDispositionResponse disposition = service.dispositionMonitoringAlert(
      alertDisposition(alert.alertId(), "DISP-IDEMP-001", ExceptionModels.AlertDispositionDecision.ESCALATE)
    );

    assertEquals(ExceptionModels.AlertStatus.OPEN, disposition.previousStatus());
    assertEquals(ExceptionModels.AlertStatus.ESCALATED, disposition.newStatus());
    assertEquals("ESCALATION_REQUIRED", disposition.reasonCode());
    assertTrue(disposition.auditRef().startsWith("AUDIT-CAD-"));
    assertEquals("ConcessionMonitoringAlertDispositioned.v1", disposition.outboxEventType());
    assertTrue(disposition.commentRedacted().contains("[REDACTED]"));
    assertFalse(disposition.commentRedacted().contains("borrower@example.com"));
    assertNotNull(disposition.dispositionHash());
  }

  @Test
  void dispositionMonitoringAlertReplaysSameIdempotencyKeyForSameDisposition() {
    ExceptionModels.ConcessionMonitoringAlertResponse alert = service.monitorConcessionPattern(
      monitoringSignal("MONITOR-IDEMP-009", ExceptionModels.MonitoringSignalType.SLA_BREACH, null, null),
      monitoringPolicy(ExceptionModels.MonitoringPolicyStatus.PUBLISHED, ExceptionModels.AlertSeverity.MEDIUM, 10, true)
    );
    ExceptionModels.ConcessionAlertDispositionCommand disposition = alertDisposition(
      alert.alertId(),
      "DISP-IDEMP-002",
      ExceptionModels.AlertDispositionDecision.ESCALATE
    );

    ExceptionModels.ConcessionAlertDispositionResponse first = service.dispositionMonitoringAlert(disposition);
    ExceptionModels.ConcessionAlertDispositionResponse replayed = service.dispositionMonitoringAlert(disposition);

    assertEquals(first.dispositionId(), replayed.dispositionId());
    assertEquals(first.dispositionHash(), replayed.dispositionHash());
  }

  @Test
  void dispositionMonitoringAlertRejectsIdempotencyKeyConflictForDifferentDisposition() {
    ExceptionModels.ConcessionMonitoringAlertResponse alert = service.monitorConcessionPattern(
      monitoringSignal("MONITOR-IDEMP-010", ExceptionModels.MonitoringSignalType.SLA_BREACH, null, null),
      monitoringPolicy(ExceptionModels.MonitoringPolicyStatus.PUBLISHED, ExceptionModels.AlertSeverity.MEDIUM, 10, true)
    );

    service.dispositionMonitoringAlert(alertDisposition(
      alert.alertId(),
      "DISP-IDEMP-003",
      ExceptionModels.AlertDispositionDecision.ESCALATE
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.dispositionMonitoringAlert(alertDisposition(
        alert.alertId(),
        "DISP-IDEMP-003",
        ExceptionModels.AlertDispositionDecision.ACKNOWLEDGE
      ))
    );

    assertEquals("IDEMPOTENCY_CONFLICT", error.code());
  }

  @Test
  void dispositionMonitoringAlertEnforcesTenantIsolation() {
    ExceptionModels.ConcessionMonitoringAlertResponse alert = service.monitorConcessionPattern(
      monitoringSignal("MONITOR-IDEMP-011", ExceptionModels.MonitoringSignalType.OVERRIDE_USAGE, null, null),
      monitoringPolicy(ExceptionModels.MonitoringPolicyStatus.PUBLISHED, ExceptionModels.AlertSeverity.MEDIUM, 10, true)
    );
    ExceptionModels.ConcessionAlertDispositionCommand wrongTenant = new ExceptionModels.ConcessionAlertDispositionCommand(
      UUID.fromString("22222222-2222-2222-2222-222222222222"),
      alert.alertId(),
      ExceptionModels.AlertDispositionDecision.ACKNOWLEDGE,
      "ACKNOWLEDGED_BY_COMPLIANCE",
      "reviewed",
      "risk-analyst-1",
      "DISP-IDEMP-004",
      "corr-monitor-pii11"
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.dispositionMonitoringAlert(wrongTenant)
    );

    assertEquals("MONITORING_ALERT_NOT_FOUND", error.code());
  }

  @Test
  void authorityMatrixGovernancePublishesAndResolvesConfiguredRoute() {
    ExceptionModels.AuthorityMatrixVersionResponse draft = service.createAuthorityMatrixDraft(authorityMatrixDraft(
      "AUTH-MATRIX-IDEMP-001",
      authorityMatrixRules()
    ));

    assertEquals(ExceptionModels.AuthorityMatrixVersionStatus.DRAFT, draft.status());
    assertTrue(draft.validationMessages().isEmpty());
    assertEquals("AuthorityMatrixDraftCreated.v1", draft.outboxEventType());

    ExceptionModels.AuthorityMatrixVersionResponse approved = service.approveAuthorityMatrixVersion(authorityMatrixApproval(
      draft.matrixVersionId(),
      draft.version(),
      "AUTH-MATRIX-APPROVE-IDEMP-001",
      "compliance-admin-1"
    ));
    assertEquals(ExceptionModels.AuthorityMatrixVersionStatus.APPROVED, approved.status());
    assertEquals("AuthorityMatrixVersionApproved.v1", approved.outboxEventType());

    ExceptionModels.AuthorityMatrixVersionResponse published = service.publishAuthorityMatrixVersion(new ExceptionModels.PublishAuthorityMatrixCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      draft.matrixVersionId(),
      "pricing-ops-publisher-1",
      "2026-06-06T00:00:00Z",
      "AUTH-MATRIX-PUBLISH-IDEMP-001",
      "corr-authority-matrix-publish",
      approved.version()
    ));
    assertEquals(ExceptionModels.AuthorityMatrixVersionStatus.PUBLISHED, published.status());
    assertEquals("AuthorityMatrixVersionPublished.v1", published.outboxEventType());

    ExceptionModels.AuthorityMatrixResolutionResponse route = service.resolveAuthorityMatrix(new ExceptionModels.ResolveAuthorityMatrixCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      "CONCESSION_REQUEST",
      Map.of("productRef", "PRODUCT-CONFIG-REF", "channel", "RETAIL"),
      "BASIS_POINTS",
      "CONFIGURED_AMOUNT_REF",
      "2026-06-07T00:00:00Z",
      "corr-authority-matrix-resolve"
    ));

    assertEquals(draft.matrixVersionId(), route.matrixVersionId());
    assertEquals("RULE-RETAIL-CONCESSION", route.matchedRuleId());
    assertEquals(List.of("pricing-desk-manager"), route.approvalSteps().get(0).roleScopeRefs());
    assertNotNull(route.routeHash());
  }

  @Test
  void authorityMatrixApprovalEnforcesSeparationOfDuties() {
    ExceptionModels.AuthorityMatrixVersionResponse draft = service.createAuthorityMatrixDraft(authorityMatrixDraft(
      "AUTH-MATRIX-IDEMP-002",
      authorityMatrixRules()
    ));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.approveAuthorityMatrixVersion(authorityMatrixApproval(
        draft.matrixVersionId(),
        draft.version(),
        "AUTH-MATRIX-APPROVE-IDEMP-002",
        "pricing-ops-admin-1"
      ))
    );

    assertEquals("SEPARATION_OF_DUTIES_VIOLATION", error.code());
  }

  @Test
  void authorityMatrixApprovalFailsClosedWhenCatchAllRuleIsMissing() {
    ExceptionModels.AuthorityMatrixVersionResponse draft = service.createAuthorityMatrixDraft(authorityMatrixDraft(
      "AUTH-MATRIX-IDEMP-003",
      List.of(authorityMatrixRule(
        "RULE-ONLY-SPECIFIC",
        Map.of("productRef", "PRODUCT-CONFIG-REF"),
        10
      ))
    ));

    assertTrue(draft.validationMessages().stream().anyMatch(message -> message.code().equals("POLICY_NOT_SATISFIED")));
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.approveAuthorityMatrixVersion(authorityMatrixApproval(
        draft.matrixVersionId(),
        draft.version(),
        "AUTH-MATRIX-APPROVE-IDEMP-003",
        "compliance-admin-1"
      ))
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
  }

  @Test
  void authorityMatrixValidationRejectsDuplicateAmbiguousRows() {
    ExceptionModels.AuthorityMatrixVersionResponse draft = service.createAuthorityMatrixDraft(authorityMatrixDraft(
      "AUTH-MATRIX-IDEMP-004",
      List.of(
        authorityMatrixRule("RULE-DUP-1", Map.of("channel", "RETAIL"), 10),
        authorityMatrixRule("RULE-DUP-2", Map.of("channel", "RETAIL"), 20),
        authorityMatrixRule("RULE-CATCH-ALL", Map.of(), 100)
      )
    ));

    assertTrue(draft.validationMessages().stream().anyMatch(message -> message.code().equals("AMBIGUOUS_AUTHORITY_MATRIX_ROW")));
  }

  @Test
  void exceptionHistoryReconstructsApprovedAppliedMonitoringAndQuoteLinks() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("HISTORY-IDEMP-001", "HISTORY-APPROVE-IDEMP-001");
    ExceptionModels.ConcessionApplicationResponse applied = service.applyApprovedConcession(applyRequest(
      created.concessionRequestId(),
      "HISTORY-APPLY-IDEMP-001",
      2,
      "quote-hash-v1",
      true
    ));
    service.monitorConcessionPattern(
      historyMonitoringSignal("HISTORY-MONITOR-IDEMP-001", created.concessionRequestId(), applied.applicationId()),
      monitoringPolicy(ExceptionModels.MonitoringPolicyStatus.PUBLISHED, ExceptionModels.AlertSeverity.HIGH, 10, true)
    );
    service.guardPriceMutation(
      manualPriceEditCommand("HISTORY-GUARD-IDEMP-001", null, ledgerExpectation("ledger-hash-before-v1")),
      priceMutationGuardPolicy()
    );

    ExceptionModels.ExceptionHistoryTimeline timeline = service.reconstructExceptionHistory(historySearch(
      ExceptionModels.ExceptionHistorySubjectType.QUOTE,
      "QUOTE-PII11",
      Set.of("exception_history.view", "exception_history.raw_json", "exception_history.evidence")
    ));

    assertTrue(timeline.events().stream().anyMatch(event -> event.action() == ExceptionModels.ExceptionHistoryAction.REQUESTED
      && event.targetQuoteId().equals("QUOTE-PII11")));
    assertTrue(timeline.events().stream().anyMatch(event -> event.action() == ExceptionModels.ExceptionHistoryAction.APPROVED));
    assertTrue(timeline.events().stream().anyMatch(event -> event.action() == ExceptionModels.ExceptionHistoryAction.APPLIED
      && event.targetQuoteId().equals("QUOTE-PII11")));
    assertTrue(timeline.events().stream().anyMatch(event -> event.action() == ExceptionModels.ExceptionHistoryAction.MONITORING_ALERT));
    assertTrue(timeline.events().stream().anyMatch(event -> event.action() == ExceptionModels.ExceptionHistoryAction.PRICE_MUTATION_BLOCKED));
    assertTrue(timeline.versionGraph().configVersions().containsValue("MATRIX-V1"));
    assertNotNull(timeline.projectionHash());
    assertTrue(timeline.auditRef().startsWith("AUDIT-HISTORY-"));
    ExceptionModels.ExceptionHistoryProjectionRecord storedProjection = repository.findHistoryProjection(
      timeline.tenantId(),
      timeline.subjectType(),
      timeline.subjectId()
    ).orElseThrow();
    assertEquals(timeline.projectionHash(), storedProjection.projectionHash());
    assertEquals(timeline.events().size(), storedProjection.timeline().events().size());
    assertTrue(repository.historyAudits().stream().anyMatch(audit -> audit.action().equals("EXCEPTION_HISTORY_SEARCHED")));
  }

  @Test
  void exceptionHistoryReplayUsesStoredConfigVersionsAndDeterministicHashes() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("HISTORY-IDEMP-002", "HISTORY-APPROVE-IDEMP-002");
    service.applyApprovedConcession(applyRequest(created.concessionRequestId(), "HISTORY-APPLY-IDEMP-002", 2, "quote-hash-v1", true));
    ExceptionModels.ExceptionHistorySearchRequest search = historySearch(
      ExceptionModels.ExceptionHistorySubjectType.CONCESSION_REQUEST,
      created.concessionRequestId(),
      Set.of("exception_history.view", "exception_history.replay")
    );

    ExceptionModels.ExceptionHistoryTimeline timeline = service.reconstructExceptionHistory(new ExceptionModels.ExceptionHistorySearchRequest(
      search.tenantId(), search.subjectType(), search.subjectId(), search.actorId(), Set.of("exception_history.view"), false, search.correlationId()
    ));
    ExceptionModels.ExceptionHistoryReplayResult first = service.replayExceptionHistory(search, timeline.projectionHash(), List.of());
    ExceptionModels.ExceptionHistoryReplayResult second = service.replayExceptionHistory(search, timeline.projectionHash(), List.of());

    assertEquals(ExceptionModels.ExceptionHistoryReplayStatus.MATCH, first.status());
    assertEquals(timeline.projectionHash(), first.actualHash());
    assertEquals(first.replayHash(), second.replayHash());
    assertTrue(first.configVersionIds().contains("MATRIX-V1"));
    assertEquals("ExceptionHistoryReplayCompleted.v1", first.outboxEventType());
    assertEquals(first.replayHash(), repository.findHistoryReplay(first.tenantId(), first.replayId()).orElseThrow().replayHash());
    assertTrue(repository.historyAudits().stream().anyMatch(audit -> audit.action().equals("EXCEPTION_HISTORY_REPLAYED")));
  }

  @Test
  void exceptionHistoryExportCreatesSignedRedactedManifestWithFieldPermissions() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("HISTORY-IDEMP-003", "HISTORY-APPROVE-IDEMP-003");
    ExceptionModels.ExceptionHistoryExportPacket export = service.exportExceptionHistory(historySearch(
      ExceptionModels.ExceptionHistorySubjectType.CONCESSION_REQUEST,
      created.concessionRequestId(),
      Set.of("exception_history.view", "exception_history.replay", "exception_history.export")
    ), true, null);

    assertEquals("ExceptionHistoryExportCreated.v1", export.outboxEventType());
    assertTrue(export.manifest().signature().startsWith("signed-sha256:"));
    assertEquals("LEAST_PRIVILEGE_REDACTED", export.manifest().redactionMode());
    assertTrue(export.manifest().excludedFields().contains("raw_event_json"));
    assertTrue(export.manifest().excludedFields().contains("evidence_refs"));
    assertTrue(export.timeline().events().stream().allMatch(ExceptionModels.TimelineEvent::fieldLevelRestricted));
    assertEquals(export.manifest().manifestHash(), repository.findHistoryExport(
      export.tenantId(),
      export.manifest().exportId()
    ).orElseThrow().manifest().manifestHash());
    assertTrue(repository.historyAudits().stream().anyMatch(audit -> audit.action().equals("EXCEPTION_HISTORY_EXPORTED")));
  }

  @Test
  void exceptionHistoryControllerExposesRequiredRestContractAndDelegates() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("HISTORY-IDEMP-API-001", "HISTORY-APPROVE-API-001");
    ExceptionHistoryController controller = new ExceptionHistoryController(service);

    ExceptionModels.ExceptionHistoryTimeline timeline = controller.getExceptionHistory(historySearch(
      ExceptionModels.ExceptionHistorySubjectType.CONCESSION_REQUEST,
      created.concessionRequestId(),
      Set.of("exception_history.view")
    ));

    assertEquals("GET /api/v1/tenants/{tenantId}/exception-history", ExceptionHistoryController.GET_EXCEPTION_HISTORY);
    assertEquals("POST /api/v1/tenants/{tenantId}/exception-history/replay", ExceptionHistoryController.POST_EXCEPTION_HISTORY_REPLAY);
    assertEquals("POST /api/v1/tenants/{tenantId}/exception-history/export", ExceptionHistoryController.POST_EXCEPTION_HISTORY_EXPORT);
    assertEquals(created.concessionRequestId(), timeline.subjectId());
  }

  @Test
  void exceptionWorkbenchAssemblesConcessionCoverageWithoutPricingRules() {
    ExceptionHistoryController controller = new ExceptionHistoryController(service);

    ExceptionModels.ExceptionWorkbenchCase workbench = controller.getExceptionConcessionWorkbench(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      "CASE-PII22-S18",
      "QUOTE-PII22-S18"
    );

    assertEquals("GET /api/v1/tenants/{tenantId}/exceptions/concessions/{caseId}/workbench", ExceptionHistoryController.GET_EXCEPTION_CONCESSION_WORKBENCH);
    assertEquals("GOVERNED_REVIEW", workbench.status());
    assertEquals("QUOTE-PII22-S18", workbench.quoteId());
    assertTrue(workbench.sections().stream().anyMatch(section -> section.sectionId().equals("concession-request")));
    assertTrue(workbench.sections().stream().anyMatch(section -> section.sectionId().equals("eligibility-exception")));
    assertTrue(workbench.sections().stream().anyMatch(section -> section.sectionId().equals("authority-matrix")));
    assertTrue(workbench.sections().stream().anyMatch(section -> section.sectionId().equals("manual-price-mutation-guard")));
    assertTrue(workbench.sections().stream().anyMatch(section -> section.sectionId().equals("risk-events")));
    assertTrue(workbench.sections().stream().anyMatch(section -> section.sectionId().equals("history-replay-export")));
    assertTrue(workbench.manualPriceMutationGuard().commitDisabled());
    assertEquals(ExceptionModels.PriceMutationGuardDecision.BLOCKED, workbench.manualPriceMutationGuard().decision());
    assertTrue(workbench.manualPriceMutationGuard().reasonCodes().contains("MANUAL_PRICE_EDIT_FORBIDDEN"));
    assertTrue(workbench.crossServiceRefs().contains("pricing-service.ledger-ref"));
    assertNotNull(workbench.replayHash());
    assertFalse(workbench.fallbackReason().toLowerCase(java.util.Locale.ROOT).contains("rate"));
  }

  @Test
  void exceptionHistoryPermissionsFailClosedForViewReplayAndExport() {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("HISTORY-IDEMP-PERM-001", "HISTORY-APPROVE-PERM-001");

    ExceptionServiceException view = assertThrows(
      ExceptionServiceException.class,
      () -> service.reconstructExceptionHistory(historySearch(
        ExceptionModels.ExceptionHistorySubjectType.CONCESSION_REQUEST,
        created.concessionRequestId(),
        Set.of()
      ))
    );
    ExceptionServiceException replay = assertThrows(
      ExceptionServiceException.class,
      () -> service.replayExceptionHistory(historySearch(
        ExceptionModels.ExceptionHistorySubjectType.CONCESSION_REQUEST,
        created.concessionRequestId(),
        Set.of("exception_history.view")
      ), "expected", List.of())
    );
    ExceptionServiceException export = assertThrows(
      ExceptionServiceException.class,
      () -> service.exportExceptionHistory(historySearch(
        ExceptionModels.ExceptionHistorySubjectType.CONCESSION_REQUEST,
        created.concessionRequestId(),
        Set.of("exception_history.view", "exception_history.replay")
      ), true, null)
    );

    assertEquals("FORBIDDEN", view.code());
    assertEquals("FORBIDDEN", replay.code());
    assertEquals("FORBIDDEN", export.code());
    assertTrue(view.getMessage().contains("exception_history.view"));
    assertTrue(replay.getMessage().contains("exception_history.replay"));
    assertTrue(export.getMessage().contains("exception_history.export"));
  }

  @Test
  void exceptionHistoryGoldenFixturesCoverApprovedAppliedAndRejectedHistories() throws IOException {
    ExceptionModels.PricingConcessionRequestStatus created = approvedConcession("HISTORY-IDEMP-004", "HISTORY-APPROVE-IDEMP-004");
    service.applyApprovedConcession(applyRequest(created.concessionRequestId(), "HISTORY-APPLY-IDEMP-004", 2, "quote-hash-v1", true));
    ExceptionModels.ExceptionRequestStatus rejected = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-REJECTED-PII11", ExceptionModels.ExceptionType.EXCEPTION)
    );
    service.transition(rejected.exceptionRequestId(), new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.SUBMITTED));
    service.transition(rejected.exceptionRequestId(), new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.REJECTED));

    ExceptionModels.ExceptionHistoryTimeline approvedTimeline = service.reconstructExceptionHistory(historySearch(
      ExceptionModels.ExceptionHistorySubjectType.CONCESSION_REQUEST,
      created.concessionRequestId(),
      Set.of("exception_history.view")
    ));
    ExceptionModels.ExceptionHistoryTimeline rejectedTimeline = service.reconstructExceptionHistory(historySearch(
      ExceptionModels.ExceptionHistorySubjectType.QUOTE,
      "QUOTE-REJECTED-PII11",
      Set.of("exception_history.view")
    ));

    assertTrue(approvedTimeline.events().stream().anyMatch(event -> event.action() == ExceptionModels.ExceptionHistoryAction.APPLIED));
    assertTrue(rejectedTimeline.events().stream().anyMatch(event -> event.action() == ExceptionModels.ExceptionHistoryAction.REJECTED));
    assertTimelineMatchesGoldenFixture(
      approvedTimeline,
      "golden/concessions/full-exception-history-approved-applied-v1.json"
    );
    assertTimelineMatchesGoldenFixture(
      rejectedTimeline,
      "golden/concessions/full-exception-history-rejected-v1.json"
    );
  }

  private static void assertTimelineMatchesGoldenFixture(
    ExceptionModels.ExceptionHistoryTimeline timeline,
    String fixturePath
  ) throws IOException {
    JsonNode fixture = JSON.readTree(Path.of(fixturePath).toFile());
    assertEquals("PII-11-S09", fixture.get("storyId").asText());
    assertEquals(fixture.get("subjectType").asText(), timeline.subjectType().name());
    for (JsonNode expectedAction : fixture.get("expectedActions")) {
      assertTrue(timeline.events().stream().anyMatch(event -> event.action().name().equals(expectedAction.asText())),
        "missing golden action " + expectedAction.asText() + " in " + fixturePath);
    }
    if (fixture.has("requiredConfigVersions")) {
      for (JsonNode expectedConfig : fixture.get("requiredConfigVersions")) {
        assertTrue(timeline.versionGraph().configVersions().containsValue(expectedConfig.asText()),
          "missing golden config version " + expectedConfig.asText() + " in " + fixturePath);
      }
    }
  }

  private static ExceptionModels.ExceptionHistorySearchRequest historySearch(
    ExceptionModels.ExceptionHistorySubjectType subjectType,
    String subjectId,
    Set<String> permissions
  ) {
    return new ExceptionModels.ExceptionHistorySearchRequest(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      subjectType,
      subjectId,
      "compliance-auditor-1",
      permissions,
      permissions.contains("exception_history.raw_json"),
      "corr-history-pii11"
    );
  }

  private static ExceptionModels.ConcessionMonitoringSignalCommand historyMonitoringSignal(
    String idempotencyKey,
    String concessionRequestId,
    String applicationId
  ) {
    return new ExceptionModels.ConcessionMonitoringSignalCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      "ConcessionAppliedToQuote:evt-history-001",
      concessionRequestId,
      applicationId,
      "APD-HISTORY",
      ExceptionModels.MonitoringSignalType.ROUTE_BYPASS,
      Map.of(
        "branchRef", "BRANCH-12",
        "channel", "RETAIL",
        "productRef", "PRODUCT-CONFIG-REF",
        "reasonCode", "RETENTION_REQUEST"
      ),
      Map.of("alertCountBucket", "configured-high"),
      null,
      null,
      "risk-analyst-1",
      idempotencyKey,
      "corr-history-pii11"
    );
  }

  private static ExceptionModels.CreateAuthorityMatrixDraftCommand authorityMatrixDraft(
    String idempotencyKey,
    List<ExceptionModels.AuthorityMatrixRuleDraft> rules
  ) {
    return new ExceptionModels.CreateAuthorityMatrixDraftCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      "tenant-authority-matrix-2026-06",
      null,
      rules,
      "pricing-ops-admin-1",
      idempotencyKey,
      "corr-authority-matrix-draft"
    );
  }

  private static ExceptionModels.ApproveAuthorityMatrixCommand authorityMatrixApproval(
    String matrixVersionId,
    int expectedVersion,
    String idempotencyKey,
    String actorId
  ) {
    return new ExceptionModels.ApproveAuthorityMatrixCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      matrixVersionId,
      actorId,
      List.of("authority_matrix.approve"),
      new ExceptionModels.ConflictAttestation(true, "I am not the draft creator"),
      "APPROVAL-TICKET-PII-11-S07",
      idempotencyKey,
      "corr-authority-matrix-approve",
      expectedVersion
    );
  }

  private static List<ExceptionModels.AuthorityMatrixRuleDraft> authorityMatrixRules() {
    return List.of(
      authorityMatrixRule("RULE-RETAIL-CONCESSION", Map.of("productRef", "PRODUCT-CONFIG-REF", "channel", "RETAIL"), 10),
      authorityMatrixRule("RULE-CATCH-ALL", Map.of(), 100)
    );
  }

  private static ExceptionModels.AuthorityMatrixRuleDraft authorityMatrixRule(
    String ruleId,
    Map<String, String> dimensions,
    int priority
  ) {
    return new ExceptionModels.AuthorityMatrixRuleDraft(
      ruleId,
      "CONCESSION_REQUEST",
      new ExceptionModels.AuthorityMatrixCondition(dimensions),
      "BASIS_POINTS",
      null,
      null,
      List.of(new ExceptionModels.AuthorityMatrixApprovalStep(
        "approval-route-step-1",
        List.of("pricing-desk-manager"),
        "SLA-POLICY-REF"
      )),
      priority,
      "NO_CONFIGURED_ROUTE_FAIL_CLOSED"
    );
  }

  private ExceptionModels.PricingConcessionRequestStatus approvedConcession(String requestIdempotencyKey, String approvalIdempotencyKey) {
    ExceptionModels.PricingConcessionRequestStatus created = service.createPricingConcession(concessionRequest(
      requestIdempotencyKey,
      "clean narrative",
      false,
      false
    ));
    service.approveConcession(approvalRequest(
      created.concessionRequestId(),
      approvalIdempotencyKey,
      "pricing-desk-manager",
      "approval-manager-1",
      created.version()
    ));
    return created;
  }

  private static ExceptionModels.CreateEligibilityExceptionRequest eligibilityExceptionRequest(
    String idempotencyKey,
    String relatedConcessionRequestId,
    boolean findingExceptionable,
    boolean requestedScenarioScope,
    boolean includeEvidence
  ) {
    return new ExceptionModels.CreateEligibilityExceptionRequest(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      "QUOTE-PII11",
      "SCENARIO-PII11",
      null,
      new ExceptionModels.EligibilityFindingRef(
        "ELIG-RESULT-001",
        "FINDING-001",
        "ELIGIBILITY-RULE-CONFIG-REF",
        "RULE-VERSION-2026-06",
        "HIGH",
        findingExceptionable,
        "eligibility-result-hash-v1"
      ),
      new ExceptionModels.EligibilityExceptionScope(
        requestedScenarioScope ? "SCENARIO" : "QUOTE",
        Map.of("scopeRef", requestedScenarioScope ? "SCENARIO-PII11" : "QUOTE-PII11")
      ),
      "CONFIGURED_ELIGIBILITY_EXCEPTION_REASON",
      "Compensating factors documented without borrower@example.com in storage",
      List.of(new ExceptionModels.CompensatingFactor("CONFIGURED_FACTOR_REF", "verified reserves evidence ref")),
      includeEvidence
        ? List.of(new ExceptionModels.EligibilityExceptionEvidenceRef(
          "evidence://eligibility/doc/123",
          "SUPPORTING_NOTE",
          "sha256:def456",
          "loan-officer-7"
        ))
        : List.of(),
      "2026-07-31",
      relatedConcessionRequestId,
      "loan-officer-7",
      idempotencyKey,
      "corr-eligibility-pii11"
    );
  }

  private static ExceptionModels.EligibilityExceptionPolicy eligibilityExceptionPolicy(
    boolean findingExceptionable,
    boolean quoteStateAllowsRequest,
    boolean requiredEvidence,
    boolean ambiguousRoute
  ) {
    return new ExceptionModels.EligibilityExceptionPolicy(
      "ELIGIBILITY-POLICY-V1",
      "MATRIX-V1",
      findingExceptionable,
      quoteStateAllowsRequest,
      requiredEvidence,
      new ExceptionModels.ApprovalRouteSnapshot(
        "MATRIX-V1",
        List.of("eligibility-exception-approver"),
        "PT4H",
        ambiguousRoute
      )
    );
  }

  private static ExceptionModels.ConcessionMonitoringSignalCommand monitoringSignal(
    String idempotencyKey,
    ExceptionModels.MonitoringSignalType signalType,
    String fairnessCohortRef,
    Integer fairnessCohortCellCount
  ) {
    return new ExceptionModels.ConcessionMonitoringSignalCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      "ConcessionRequestApproved:evt-001",
      "PCR-PII11",
      null,
      "APD-PII11",
      signalType,
      Map.of(
        "branchRef", "BRANCH-12",
        "channel", "RETAIL",
        "productRef", "PRODUCT-CONFIG-REF",
        "reasonCode", "RETENTION_REQUEST"
      ),
      Map.of(
        "approvalCountBucket", "configured-high",
        "windowRef", "monitoring-window-24h"
      ),
      fairnessCohortRef,
      fairnessCohortCellCount,
      "risk-analyst-1",
      idempotencyKey,
      "corr-monitor-pii11"
    );
  }

  private static ExceptionModels.MonitoringPolicyVersion monitoringPolicy(
    ExceptionModels.MonitoringPolicyStatus status,
    ExceptionModels.AlertSeverity severity,
    Integer minimumCellSize,
    boolean fairnessCohortViewAuthorized
  ) {
    return new ExceptionModels.MonitoringPolicyVersion(
      "concession-frequency-detector",
      "detector-version-2026-06",
      status,
      "monitoring-policy://tenant/111/frequency/v1",
      severity,
      "PT24H",
      List.of("branchRef", "channel", "productRef", "reasonCode"),
      minimumCellSize,
      fairnessCohortViewAuthorized
    );
  }

  private static ExceptionModels.ConcessionAlertDispositionCommand alertDisposition(
    String alertId,
    String idempotencyKey,
    ExceptionModels.AlertDispositionDecision decision
  ) {
    return new ExceptionModels.ConcessionAlertDispositionCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      alertId,
      decision,
      "ESCALATION_REQUIRED",
      "Escalated with borrower@example.com removed from analyst note",
      "risk-analyst-1",
      idempotencyKey,
      "corr-monitor-pii11"
    );
  }

  private static ExceptionModels.PriceMutationGuardPolicyVersion priceMutationGuardPolicy() {
    return new ExceptionModels.PriceMutationGuardPolicyVersion(
      "PRICE-GUARD-POLICY-V1",
      ExceptionModels.PriceMutationGuardPolicyStatus.PUBLISHED,
      List.of("basePrice", "adjustments", "concession", "margin", "finalPrice", "lockPrice"),
      List.of("ApplyApprovedConcession", "AppendPricingLedgerAdjustment"),
      List.of(
        "workflow-capability://tenant/111/apply-approved-concession",
        "workflow-capability://tenant/111/pricing-ledger-adjustment"
      ),
      "2026-06-06T00:00:00Z"
    );
  }

  private static ExceptionModels.GuardPriceMutationCommand manualPriceEditCommand(
    String idempotencyKey,
    ExceptionModels.AuthorizedPricingCommandRef authorizedCommandRef,
    ExceptionModels.LedgerHashExpectation ledgerHashExpectation
  ) {
    return new ExceptionModels.GuardPriceMutationCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      ExceptionModels.PriceMutationTargetType.PARTNER_API,
      "QUOTE-PII11",
      null,
      "manual-price-edit-api",
      List.of(
        new ExceptionModels.PriceFieldMutation("finalPrice", "hash-final-before", "hash-final-after"),
        new ExceptionModels.PriceFieldMutation("margin", "hash-margin-before", "hash-margin-after")
      ),
      authorizedCommandRef,
      ledgerHashExpectation,
      "partner-api-client-1",
      idempotencyKey,
      "corr-price-guard-pii11"
    );
  }

  private static ExceptionModels.LedgerHashExpectation ledgerExpectation(String expectedLedgerHash) {
    return new ExceptionModels.LedgerHashExpectation(
      expectedLedgerHash,
      "quote-hash-before-v1",
      null
    );
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

  private static ExceptionModels.ApplyApprovedConcessionRequest applyRequest(
    String concessionRequestId,
    String idempotencyKey,
    int expectedRequestVersion,
    String currentQuoteSnapshotHash,
    boolean eligibilityExceptionsResolved
  ) {
    return new ExceptionModels.ApplyApprovedConcessionRequest(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      concessionRequestId,
      new ExceptionModels.ApplicationTarget(
        ExceptionModels.ApplicationTargetType.QUOTE,
        "QUOTE-PII11",
        null,
        currentQuoteSnapshotHash,
        null
      ),
      expectedRequestVersion,
      "quote-hash-v1",
      "ledger-hash-before-v1",
      "PRICING-RULE-V1",
      "POLICY-V1",
      new ExceptionModels.ApplicationPrecedence("PRECEDENCE-V1", 8, "HALF_UP"),
      eligibilityExceptionsResolved,
      "pricing-desk-user-1",
      idempotencyKey,
      "corr-apply-pii11"
    );
  }
}
