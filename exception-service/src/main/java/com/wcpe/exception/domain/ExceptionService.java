package com.wcpe.exception.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mock-backed exception lifecycle service for the PII-11 walking skeleton.
 */
public class ExceptionService {

  private static final boolean MOCK_BACKED = true;
  private static final boolean AUTHORITATIVE_INTEGRATION = false;

  private final ExceptionRepository repository;

  public ExceptionService(ExceptionRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  public ExceptionModels.ExceptionRequestStatus create(ExceptionModels.ExceptionRequestCreate request) {
    if (request == null || isBlank(request.placeholderQuoteReference())) {
      throw new ExceptionServiceException(
        "MISSING_PLACEHOLDER_QUOTE_REFERENCE",
        "placeholderQuoteReference is required"
      );
    }
    if (request.requestType() == null) {
      throw new ExceptionServiceException("MISSING_REQUEST_TYPE", "requestType is required");
    }

    return toStatus(repository.create(request));
  }

  public ExceptionModels.PricingConcessionRequestStatus createPricingConcession(
    ExceptionModels.PricingConcessionRequestCreate request
  ) {
    validateConcessionRequest(request);
    String commentsRedacted = redactNarrative(request.narrative());
    String approvalRouteHash = hash(String.join("|", request.approvalRouteSnapshot().approverGroups()));
    String requestHash = concessionRequestHash(request, commentsRedacted, approvalRouteHash);

    return repository.findConcessionByIdempotencyKey(request.tenantId(), request.idempotencyKey())
      .map(existing -> {
        if (!existing.requestHash().equals(requestHash)) {
          throw new ExceptionServiceException(
            "DUPLICATE_IDEMPOTENCY_KEY",
            "Idempotency-Key already exists for a different concession request"
          );
        }
        return toConcessionStatus(existing);
      })
      .orElseGet(() -> toConcessionStatus(repository.createConcession(
        normalizeConcessionRequest(request),
        request.eligibilityExceptionRequired()
          ? ExceptionModels.ConcessionRequestStatus.NEEDS_ELIGIBILITY_EXCEPTION
          : ExceptionModels.ConcessionRequestStatus.SUBMITTED,
        commentsRedacted,
        approvalRouteHash,
        requestHash
      )));
  }

  public ExceptionModels.PricingConcessionRequestStatus pricingConcessionStatus(String tenantId, String concessionRequestId) {
    return repository.findConcessionById(tenantId, concessionRequestId)
      .map(this::toConcessionStatus)
      .orElseThrow(() -> new ExceptionServiceException(
        "UNKNOWN_CONCESSION_REQUEST",
        "Unknown concession request id for tenant scope: " + concessionRequestId
      ));
  }

  public ExceptionModels.ConcessionApprovalResponse approveConcession(
    ExceptionModels.ApproveConcessionRequest approval
  ) {
    validateApprovalRequest(approval);
    ExceptionModels.PricingConcessionRequestRecord concession = repository
      .findConcessionById(approval.tenantId().toString(), approval.concessionRequestId())
      .orElseThrow(() -> new ExceptionServiceException(
        "UNKNOWN_CONCESSION_REQUEST",
        "Unknown concession request id for tenant scope: " + approval.concessionRequestId()
      ));
    var replayed = repository.findApprovalByIdempotencyKey(approval.tenantId(), approval.idempotencyKey());
    if (replayed.isPresent()) {
      if (!approvalMatchesExisting(approval, replayed.get())) {
        throw new ExceptionServiceException(
          "IDEMPOTENCY_CONFLICT",
          "Idempotency-Key already exists for a different approval decision"
        );
      }
      return toApprovalResponse(replayed.get(), ExceptionModels.ConcessionRequestStatus.SUBMITTED);
    }
    validateApprovalAgainstConcession(approval, concession);

    String commentRedacted = redactNarrative(approval.comment());
    String eventHash = approvalEventHash(approval, concession, commentRedacted);
    return toApprovalResponse(
      repository.approveConcession(concession, normalizeApprovalRequest(approval), commentRedacted, eventHash),
      concession.status()
    );
  }

  public ExceptionModels.ExceptionRequestStatus status(String exceptionRequestId) {
    return repository.findById(exceptionRequestId)
      .map(this::toStatus)
      .orElseThrow(() -> unknownRequest(exceptionRequestId));
  }

  public ExceptionModels.ExceptionTransitionResponse transition(
    String exceptionRequestId,
    ExceptionModels.ExceptionTransitionRequest request
  ) {
    if (request == null || request.requestedTransition() == null) {
      throw new ExceptionServiceException("UNKNOWN_TARGET_STATE", "requestedTransition is required");
    }

    ExceptionModels.ExceptionRequestRecord existing = repository.findById(exceptionRequestId)
      .orElseThrow(() -> unknownRequest(exceptionRequestId));
    ExceptionModels.ExceptionState previousState = existing.state();

    ExceptionModels.ExceptionRequestRecord updated = repository
      .transition(exceptionRequestId, request.requestedTransition())
      .orElseThrow(() -> unknownRequest(exceptionRequestId));

    return new ExceptionModels.ExceptionTransitionResponse(
      updated.exceptionRequestId(),
      previousState,
      updated.state(),
      request.requestedTransition(),
      MOCK_BACKED,
      AUTHORITATIVE_INTEGRATION,
      updated.updatedAt()
    );
  }

  public ExceptionModels.ExceptionError toError(ExceptionServiceException exception, String requestId) {
    return new ExceptionModels.ExceptionError(exception.code(), exception.getMessage(), requestId);
  }

  private ExceptionModels.ExceptionRequestStatus toStatus(ExceptionModels.ExceptionRequestRecord record) {
    return new ExceptionModels.ExceptionRequestStatus(
      record.exceptionRequestId(),
      record.placeholderQuoteReference(),
      record.requestType(),
      record.state(),
      MOCK_BACKED,
      AUTHORITATIVE_INTEGRATION,
      record.createdAt(),
      record.updatedAt()
    );
  }

  private ExceptionModels.PricingConcessionRequestStatus toConcessionStatus(
    ExceptionModels.PricingConcessionRequestRecord record
  ) {
    return new ExceptionModels.PricingConcessionRequestStatus(
      record.tenantId(),
      record.concessionRequestId(),
      record.quoteId(),
      record.scenarioId(),
      record.lockId(),
      record.status(),
      record.requestedAmount(),
      record.reasonCode(),
      record.commentsRedacted(),
      record.evidenceRefs(),
      record.concessionPolicyVersionId(),
      record.authorityMatrixVersionId(),
      record.reasonCodeVersionId(),
      record.quoteSnapshotHash(),
      record.approvalRouteHash(),
      record.nextApproverGroups(),
      record.sla(),
      record.idempotencyKey(),
      record.actorId(),
      record.correlationId(),
      record.auditRef(),
      record.outboxEventType(),
      record.requestHash(),
      record.version(),
      record.createdAt(),
      record.updatedAt()
    );
  }

  private static ExceptionModels.PricingConcessionRequestCreate normalizeConcessionRequest(
    ExceptionModels.PricingConcessionRequestCreate request
  ) {
    return new ExceptionModels.PricingConcessionRequestCreate(
      request.tenantId(),
      request.quoteId().trim(),
      request.scenarioId().trim(),
      isBlank(request.lockId()) ? null : request.lockId().trim(),
      request.requestedAmount(),
      request.reasonCode().trim(),
      request.narrative(),
      request.evidenceRefs() == null ? List.of() : List.copyOf(request.evidenceRefs()),
      request.expiration(),
      request.actorId().trim(),
      request.idempotencyKey().trim(),
      request.correlationId().trim(),
      request.concessionPolicyVersionId().trim(),
      request.reasonCodeVersionId().trim(),
      request.quoteSnapshotHash().trim(),
      request.approvalRouteSnapshot(),
      request.eligibilityExceptionRequired()
    );
  }

  private static void validateConcessionRequest(ExceptionModels.PricingConcessionRequestCreate request) {
    if (request == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "pricing concession request is required");
    }
    requireTenant(request.tenantId());
    requireText(request.quoteId(), "QUOTE_NOT_FOUND", "quoteId is required");
    requireText(request.scenarioId(), "VALIDATION_FAILED", "scenarioId is required");
    requireText(request.actorId(), "FORBIDDEN", "actorId is required");
    requireText(request.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(request.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    requireText(request.reasonCode(), "POLICY_NOT_SATISFIED", "configured reasonCode is required");
    requireText(request.concessionPolicyVersionId(), "POLICY_NOT_SATISFIED", "concessionPolicyVersionId is required");
    requireText(request.reasonCodeVersionId(), "POLICY_NOT_SATISFIED", "reasonCodeVersionId is required");
    requireText(request.quoteSnapshotHash(), "QUOTE_STATE_INVALID", "quoteSnapshotHash is required");
    validateAmount(request.requestedAmount());
    validateApprovalRoute(request.approvalRouteSnapshot());
  }

  private static void validateAmount(ExceptionModels.ConcessionAmount amount) {
    if (amount == null || amount.unit() == null || isBlank(amount.value())) {
      throw new ExceptionServiceException("VALUE_OUTSIDE_CONFIGURED_POLICY", "configured concession amount is required");
    }
    if (amount.unit() == ExceptionModels.ConcessionUnit.FEE_AMOUNT && isBlank(amount.currency())) {
      throw new ExceptionServiceException("VALUE_OUTSIDE_CONFIGURED_POLICY", "currency is required for fee amount concessions");
    }
  }

  private static void validateApprovalRoute(ExceptionModels.ApprovalRouteSnapshot route) {
    if (route == null || route.ambiguous() || isBlank(route.authorityMatrixVersionId())
      || route.approverGroups() == null || route.approverGroups().isEmpty()) {
      throw new ExceptionServiceException(
        "AUTHORITY_ROUTE_UNRESOLVED",
        "unambiguous active authority route is required"
      );
    }
  }

  private static ExceptionModels.ApproveConcessionRequest normalizeApprovalRequest(
    ExceptionModels.ApproveConcessionRequest approval
  ) {
    return new ExceptionModels.ApproveConcessionRequest(
      approval.tenantId(),
      approval.concessionRequestId().trim(),
      approval.routeStepId().trim(),
      approval.decision(),
      approval.reasonCode().trim(),
      approval.comment(),
      normalizeApprovalConditions(approval.conditions()),
      approval.conflictAttestation(),
      approval.authorityMatrixVersionId().trim(),
      approval.actorId().trim(),
      List.copyOf(approval.actorRoleRefs()),
      approval.idempotencyKey().trim(),
      approval.correlationId().trim(),
      approval.expectedRequestVersion()
    );
  }

  private static void validateApprovalRequest(ExceptionModels.ApproveConcessionRequest approval) {
    if (approval == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "approval request is required");
    }
    requireTenant(approval.tenantId());
    requireText(approval.concessionRequestId(), "VALIDATION_FAILED", "concessionRequestId is required");
    requireText(approval.routeStepId(), "NOT_CURRENT_APPROVER", "routeStepId is required");
    if (approval.decision() != ExceptionModels.ApprovalDecisionType.APPROVE) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "decision must be APPROVE");
    }
    requireText(approval.reasonCode(), "POLICY_NOT_SATISFIED", "configured approval reasonCode is required");
    requireText(approval.authorityMatrixVersionId(), "MATRIX_VERSION_RETIRED", "authorityMatrixVersionId is required");
    requireText(approval.actorId(), "FORBIDDEN", "actorId is required");
    requireText(approval.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(approval.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    if (approval.actorRoleRefs() == null || approval.actorRoleRefs().isEmpty()) {
      throw new ExceptionServiceException("NOT_CURRENT_APPROVER", "actorRoleRefs are required");
    }
    if (approval.conflictAttestation() == null || !approval.conflictAttestation().noConflict()) {
      throw new ExceptionServiceException(
        "SEPARATION_OF_DUTIES_VIOLATION",
        "approver must attest no conflict of interest"
      );
    }
  }

  private static void validateApprovalAgainstConcession(
    ExceptionModels.ApproveConcessionRequest approval,
    ExceptionModels.PricingConcessionRequestRecord concession
  ) {
    if (concession.status() == ExceptionModels.ConcessionRequestStatus.NEEDS_ELIGIBILITY_EXCEPTION) {
      throw new ExceptionServiceException(
        "ELIGIBILITY_EXCEPTION_REQUIRED",
        "concession requires eligibility exception before final approval"
      );
    }
    if (concession.status() != ExceptionModels.ConcessionRequestStatus.SUBMITTED) {
      throw new ExceptionServiceException("REQUEST_STATUS_INVALID", "only submitted concession requests can be approved");
    }
    if (approval.expectedRequestVersion() != concession.version()) {
      throw new ExceptionServiceException("STALE_REQUEST_VERSION", "expectedRequestVersion does not match current version");
    }
    if (!concession.authorityMatrixVersionId().equals(approval.authorityMatrixVersionId())) {
      throw new ExceptionServiceException("MATRIX_VERSION_RETIRED", "authority matrix version is no longer current for this request");
    }
    if (concession.actorId().equals(approval.actorId())) {
      throw new ExceptionServiceException(
        "SEPARATION_OF_DUTIES_VIOLATION",
        "requester cannot approve own concession request"
      );
    }
    if (approval.actorRoleRefs().stream().noneMatch(concession.nextApproverGroups()::contains)) {
      throw new ExceptionServiceException("NOT_CURRENT_APPROVER", "actor does not satisfy current approval route step");
    }
  }

  private ExceptionModels.ConcessionApprovalResponse toApprovalResponse(
    ExceptionModels.ApprovalDecisionRecord decision,
    ExceptionModels.ConcessionRequestStatus previousStatus
  ) {
    return new ExceptionModels.ConcessionApprovalResponse(
      decision.tenantId(),
      decision.concessionRequestId(),
      decision.decisionId(),
      previousStatus,
      ExceptionModels.ConcessionRequestStatus.APPROVED_PENDING_APPLICATION,
      decision.routeStepId(),
      null,
      decision.aggregateVersion(),
      decision.auditRef(),
      decision.outboxEventType(),
      decision.eventHash(),
      decision.correlationId(),
      decision.createdAt()
    );
  }

  private static boolean approvalMatchesExisting(
    ExceptionModels.ApproveConcessionRequest approval,
    ExceptionModels.ApprovalDecisionRecord existing
  ) {
    return existing.concessionRequestId().equals(approval.concessionRequestId())
      && existing.routeStepId().equals(approval.routeStepId())
      && existing.decision() == approval.decision()
      && existing.decisionReasonCode().equals(approval.reasonCode())
      && existing.decisionCommentRedacted().equals(redactNarrative(approval.comment()))
      && existing.conditions().equals(normalizeApprovalConditions(approval.conditions()))
      && existing.conflictAttestation().equals(approval.conflictAttestation())
      && existing.authorityMatrixVersionId().equals(approval.authorityMatrixVersionId())
      && existing.actorId().equals(approval.actorId())
      && existing.actorRoleRefs().equals(approval.actorRoleRefs());
  }

  private static ExceptionModels.ApprovalConditions normalizeApprovalConditions(
    ExceptionModels.ApprovalConditions conditions
  ) {
    if (conditions == null) {
      return new ExceptionModels.ApprovalConditions(null, Map.of());
    }
    return new ExceptionModels.ApprovalConditions(
      conditions.expiresAt(),
      conditions.conditions() == null ? Map.of() : Map.copyOf(conditions.conditions())
    );
  }

  private static void requireTenant(UUID tenantId) {
    if (tenantId == null) {
      throw new ExceptionServiceException("TENANT_ACCESS_DENIED", "tenantId is required");
    }
  }

  private static void requireText(String value, String code, String message) {
    if (isBlank(value)) {
      throw new ExceptionServiceException(code, message);
    }
  }

  private static String redactNarrative(String narrative) {
    if (isBlank(narrative)) {
      return "";
    }
    return narrative.replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "[REDACTED]")
      .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[REDACTED]");
  }

  private static String concessionRequestHash(
    ExceptionModels.PricingConcessionRequestCreate request,
    String commentsRedacted,
    String approvalRouteHash
  ) {
    ExceptionModels.ConcessionAmount amount = request.requestedAmount();
    return hash(String.join("|",
      request.tenantId().toString(),
      request.quoteId(),
      request.scenarioId(),
      Objects.toString(request.lockId(), ""),
      amount.unit().name(),
      amount.value(),
      Objects.toString(amount.currency(), ""),
      request.reasonCode(),
      commentsRedacted,
      Objects.toString(request.evidenceRefs(), ""),
      Objects.toString(request.expiration(), ""),
      request.actorId(),
      request.concessionPolicyVersionId(),
      request.reasonCodeVersionId(),
      request.quoteSnapshotHash(),
      request.approvalRouteSnapshot().authorityMatrixVersionId(),
      approvalRouteHash
    ));
  }

  private static String approvalEventHash(
    ExceptionModels.ApproveConcessionRequest approval,
    ExceptionModels.PricingConcessionRequestRecord concession,
    String commentRedacted
  ) {
    return hash(String.join("|",
      approval.tenantId().toString(),
      approval.concessionRequestId(),
      approval.routeStepId(),
      approval.decision().name(),
      approval.reasonCode(),
      commentRedacted,
      Objects.toString(approval.conditions(), ""),
      approval.authorityMatrixVersionId(),
      approval.actorId(),
      Objects.toString(approval.actorRoleRefs(), ""),
      concession.status().name(),
      ExceptionModels.ConcessionRequestStatus.APPROVED_PENDING_APPLICATION.name(),
      String.valueOf(concession.version())
    ));
  }

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private ExceptionServiceException unknownRequest(String exceptionRequestId) {
    return new ExceptionServiceException(
      "UNKNOWN_EXCEPTION_REQUEST",
      "Unknown exception request id: " + exceptionRequestId
    );
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
