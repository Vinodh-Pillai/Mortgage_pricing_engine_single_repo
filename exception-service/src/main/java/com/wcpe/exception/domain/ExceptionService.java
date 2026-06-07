package com.wcpe.exception.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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

  public ExceptionModels.AuthorityMatrixVersionResponse createAuthorityMatrixDraft(
    ExceptionModels.CreateAuthorityMatrixDraftCommand command
  ) {
    ExceptionModels.CreateAuthorityMatrixDraftCommand normalized = normalizeAuthorityMatrixDraftCommand(command);
    List<ExceptionModels.AuthorityMatrixRuleDraft> rules = normalizeAuthorityMatrixRules(normalized.rules());
    List<ExceptionModels.AuthorityMatrixValidationMessage> validationMessages = validateAuthorityMatrixRules(rules);
    String validationHash = hash(rules.toString() + "|" + validationMessages.toString());
    String requestHash = authorityMatrixDraftHash(normalized, rules, validationHash);

    var replayed = repository.findAuthorityMatrixByIdempotencyKey(normalized.tenantId(), normalized.idempotencyKey());
    if (replayed.isPresent()) {
      if (!replayed.get().requestHash().equals(requestHash)) {
        throw new ExceptionServiceException(
          "IDEMPOTENCY_CONFLICT",
          "Idempotency-Key already exists for a different authority matrix draft"
        );
      }
      return toAuthorityMatrixResponse(replayed.get());
    }

    return toAuthorityMatrixResponse(repository.createAuthorityMatrixDraft(
      normalized,
      rules,
      validationMessages,
      validationHash,
      requestHash
    ));
  }

  public ExceptionModels.AuthorityMatrixVersionResponse approveAuthorityMatrixVersion(
    ExceptionModels.ApproveAuthorityMatrixCommand command
  ) {
    validateApproveAuthorityMatrixCommand(command);
    ExceptionModels.AuthorityMatrixVersionRecord draft = repository
      .findAuthorityMatrixById(command.tenantId(), command.matrixVersionId().trim())
      .orElseThrow(() -> new ExceptionServiceException(
        "AUTHORITY_MATRIX_NOT_FOUND",
        "Unknown authority matrix version for tenant scope: " + command.matrixVersionId()
      ));
    if (draft.status() != ExceptionModels.AuthorityMatrixVersionStatus.DRAFT) {
      throw new ExceptionServiceException("REQUEST_STATUS_INVALID", "only draft authority matrix versions can be approved");
    }
    requireVersion(command.expectedVersion(), draft.version());
    if (!draft.validationMessages().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "authority matrix validation must pass before approval");
    }
    if (draft.submittedBy().equals(command.actorId().trim())) {
      throw new ExceptionServiceException("SEPARATION_OF_DUTIES_VIOLATION", "draft creator cannot approve own authority matrix");
    }
    Instant now = Instant.now();
    String eventHash = hash(String.join("|",
      draft.matrixVersionId(), draft.validationHash(), command.actorId().trim(), command.approvalTicketRef().trim()
    ));
    return toAuthorityMatrixResponse(repository.saveAuthorityMatrixVersion(new ExceptionModels.AuthorityMatrixVersionRecord(
      draft.tenantId(), draft.matrixVersionId(), ExceptionModels.AuthorityMatrixVersionStatus.APPROVED,
      draft.versionLabel(), draft.sourceVersionId(), draft.rules(), draft.validationMessages(), draft.validationHash(),
      command.approvalTicketRef().trim(), draft.submittedBy(), command.actorId().trim(), null, null,
      draft.auditRef(), "AuthorityMatrixVersionApproved.v1", eventHash, draft.requestHash(), draft.idempotencyKey(),
      command.correlationId().trim(), draft.version() + 1, draft.createdAt(), now
    )));
  }

  public ExceptionModels.AuthorityMatrixVersionResponse publishAuthorityMatrixVersion(
    ExceptionModels.PublishAuthorityMatrixCommand command
  ) {
    validatePublishAuthorityMatrixCommand(command);
    ExceptionModels.AuthorityMatrixVersionRecord approved = repository
      .findAuthorityMatrixById(command.tenantId(), command.matrixVersionId().trim())
      .orElseThrow(() -> new ExceptionServiceException(
        "AUTHORITY_MATRIX_NOT_FOUND",
        "Unknown authority matrix version for tenant scope: " + command.matrixVersionId()
      ));
    if (approved.status() != ExceptionModels.AuthorityMatrixVersionStatus.APPROVED) {
      throw new ExceptionServiceException("REQUEST_STATUS_INVALID", "only approved authority matrix versions can be published");
    }
    requireVersion(command.expectedVersion(), approved.version());
    Instant effectiveFrom = parseInstant(command.effectiveFrom(), "effectiveFrom");
    Instant now = Instant.now();
    repository.suspendPublishedAuthorityMatrices(command.tenantId(), approved.matrixVersionId(), now);
    String eventHash = hash(String.join("|",
      approved.matrixVersionId(), approved.validationHash(), command.actorId().trim(), effectiveFrom.toString()
    ));
    return toAuthorityMatrixResponse(repository.saveAuthorityMatrixVersion(new ExceptionModels.AuthorityMatrixVersionRecord(
      approved.tenantId(), approved.matrixVersionId(), ExceptionModels.AuthorityMatrixVersionStatus.PUBLISHED,
      approved.versionLabel(), approved.sourceVersionId(), approved.rules(), approved.validationMessages(), approved.validationHash(),
      approved.approvalTicketRef(), approved.submittedBy(), approved.approvedBy(), command.actorId().trim(),
      effectiveFrom.toString(), approved.auditRef(), "AuthorityMatrixVersionPublished.v1", eventHash, approved.requestHash(),
      approved.idempotencyKey(), command.correlationId().trim(), approved.version() + 1, approved.createdAt(), now
    )));
  }

  public ExceptionModels.AuthorityMatrixResolutionResponse resolveAuthorityMatrix(
    ExceptionModels.ResolveAuthorityMatrixCommand command
  ) {
    validateResolveAuthorityMatrixCommand(command);
    Instant effectiveAt = parseInstant(command.effectiveAt(), "effectiveAt");
    ExceptionModels.AuthorityMatrixVersionRecord active = repository.findPublishedAuthorityMatrix(command.tenantId(), effectiveAt)
      .orElseThrow(() -> new ExceptionServiceException(
        "AUTHORITY_ROUTE_UNRESOLVED",
        "no published authority matrix version is active for tenant and timestamp"
      ));
    Map<String, String> requestDimensions = normalizedMap(command.dimensions());
    ExceptionModels.AuthorityMatrixRuleDraft rule = active.rules().stream()
      .filter(candidate -> requestTypeMatches(candidate.requestType(), command.requestType().trim()))
      .filter(candidate -> Objects.equals(candidate.amountUnit(), command.amountUnit().trim()))
      .filter(candidate -> dimensionsMatch(candidate.condition().dimensions(), requestDimensions))
      .sorted(Comparator
        .comparingInt(ExceptionModels.AuthorityMatrixRuleDraft::priority)
        .thenComparing((left, right) -> Integer.compare(
          right.condition().dimensions().size(),
          left.condition().dimensions().size()
        )))
      .findFirst()
      .orElseThrow(() -> new ExceptionServiceException(
        "AUTHORITY_ROUTE_UNRESOLVED",
        "no deterministic authority matrix rule matched the request context"
      ));
    if (rule.approvalSteps().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "matched authority matrix rule has no configured approval route");
    }
    String routeHash = hash(active.matrixVersionId() + "|" + rule.ruleId() + "|" + rule.approvalSteps().toString());
    return new ExceptionModels.AuthorityMatrixResolutionResponse(
      active.tenantId(),
      active.matrixVersionId(),
      rule.ruleId(),
      rule.approvalSteps(),
      routeHash,
      rule.failClosedReason(),
      command.correlationId().trim()
    );
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

  public ExceptionModels.EligibilityExceptionRequestResponse createEligibilityExceptionRequest(
    ExceptionModels.CreateEligibilityExceptionRequest request,
    ExceptionModels.EligibilityExceptionPolicy policy
  ) {
    validateEligibilityExceptionRequest(request, policy);
    ExceptionModels.CreateEligibilityExceptionRequest normalized = normalizeEligibilityExceptionRequest(request);
    ExceptionModels.EligibilityExceptionPolicy normalizedPolicy = normalizeEligibilityExceptionPolicy(policy);

    String narrativeRedacted = redactNarrative(normalized.narrative());
    String approvalRouteHash = hash(String.join("|", normalizedPolicy.approvalRouteSnapshot().approverGroups()));
    String requestHash = eligibilityExceptionRequestHash(normalized, normalizedPolicy, narrativeRedacted, approvalRouteHash);
    var replayed = repository.findEligibilityExceptionByIdempotencyKey(normalized.tenantId(), normalized.idempotencyKey());
    if (replayed.isPresent()) {
      if (!replayed.get().requestHash().equals(requestHash)) {
        throw new ExceptionServiceException(
          "DUPLICATE_IDEMPOTENCY_KEY",
          "Idempotency-Key already exists for a different eligibility exception request"
        );
      }
      return toEligibilityExceptionResponse(replayed.get());
    }
    repository.findActiveEligibilityException(normalized.tenantId(), normalized.findingRef(), normalized.exceptionScope())
      .ifPresent(existing -> {
        throw new ExceptionServiceException(
          "DUPLICATE_ACTIVE_EXCEPTION_REQUEST",
          "active eligibility exception request already exists for finding and scope"
        );
      });
    if (!isBlank(normalized.relatedConcessionRequestId())) {
      repository.findConcessionById(normalized.tenantId().toString(), normalized.relatedConcessionRequestId())
        .orElseThrow(() -> new ExceptionServiceException(
          "UNKNOWN_CONCESSION_REQUEST",
          "related concession request does not exist for tenant scope: " + normalized.relatedConcessionRequestId()
        ));
    }

    String eventHash = eligibilityExceptionEventHash(normalized, normalizedPolicy, requestHash, approvalRouteHash);
    return toEligibilityExceptionResponse(repository.createEligibilityException(
      normalized,
      normalizedPolicy,
      narrativeRedacted,
      approvalRouteHash,
      requestHash,
      eventHash
    ));
  }

  public ExceptionModels.EligibilityExceptionRequestResponse eligibilityExceptionRequestStatus(
    UUID tenantId,
    String exceptionRequestId
  ) {
    requireTenant(tenantId);
    requireText(exceptionRequestId, "VALIDATION_FAILED", "exceptionRequestId is required");
    return repository.findEligibilityExceptionById(tenantId, exceptionRequestId.trim())
      .map(ExceptionService::toEligibilityExceptionResponse)
      .orElseThrow(() -> new ExceptionServiceException(
        "ELIGIBILITY_EXCEPTION_REQUEST_NOT_FOUND",
        "Unknown eligibility exception request id for tenant scope: " + exceptionRequestId
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

  public ExceptionModels.ConcessionApplicationResponse applyApprovedConcession(
    ExceptionModels.ApplyApprovedConcessionRequest command
  ) {
    validateApplyRequest(command);
    ExceptionModels.PricingConcessionRequestRecord concession = repository
      .findConcessionById(command.tenantId().toString(), command.concessionRequestId())
      .orElseThrow(() -> new ExceptionServiceException(
        "UNKNOWN_CONCESSION_REQUEST",
        "Unknown concession request id for tenant scope: " + command.concessionRequestId()
      ));

    var replayed = repository.findApplicationByIdempotencyKey(command.tenantId(), command.idempotencyKey());
    if (replayed.isPresent()) {
      if (!applicationMatchesExisting(command, replayed.get())) {
        throw new ExceptionServiceException(
          "IDEMPOTENCY_CONFLICT",
          "Idempotency-Key already exists for a different concession application"
        );
      }
      return toApplicationResponse(replayed.get());
    }

    validateApplyAgainstConcession(command, concession);
    if (repository.findApplicationByTarget(command.tenantId(), command.concessionRequestId(), command.target()).isPresent()) {
      throw new ExceptionServiceException("ALREADY_APPLIED", "concession request is already applied to this target");
    }

    String pricingLedgerEntryId = "LEDGER-" + hash(command.tenantId() + ":" + command.concessionRequestId()
      + ":" + command.target().targetType() + ":" + command.expectedLedgerHash()).substring(0, 16);
    String afterPriceHash = hash(String.join("|",
      command.expectedLedgerHash(),
      concession.requestHash(),
      command.pricingRuleVersionId(),
      command.policyVersionId(),
      command.precedence().precedenceConfigVersionId(),
      String.valueOf(command.precedence().scale()),
      command.precedence().roundingMode()
    ));
    String replayHash = applyReplayHash(command, concession, pricingLedgerEntryId, afterPriceHash);
    String outboxEventType = command.target().targetType() == ExceptionModels.ApplicationTargetType.LOCK
      ? "ConcessionAppliedToLock.v1"
      : "ConcessionAppliedToQuote.v1";

    return toApplicationResponse(repository.applyConcession(
      concession,
      normalizeApplyRequest(command),
      pricingLedgerEntryId,
      afterPriceHash,
      replayHash,
      outboxEventType
    ));
  }

  public ExceptionModels.PriceMutationGuardResponse guardPriceMutation(
    ExceptionModels.GuardPriceMutationCommand command,
    ExceptionModels.PriceMutationGuardPolicyVersion policy
  ) {
    validateGuardCommand(command);
    ExceptionModels.PriceMutationGuardPolicyVersion normalizedPolicy = normalizeGuardPolicy(policy);
    ExceptionModels.GuardPriceMutationCommand normalized = normalizeGuardCommand(command);
    List<String> guardedMutations = guardedMutationFields(normalized.fieldMutations(), normalizedPolicy.guardedFields());
    if (guardedMutations.isEmpty()) {
      String replayHash = hash(String.join("|",
        normalized.tenantId().toString(), normalized.targetType().name(), normalized.quoteId(),
        normalized.fieldMutations().toString(), normalizedPolicy.policyVersionId(), "ALLOWED_UNGUARDED"
      ));
      return new ExceptionModels.PriceMutationGuardResponse(
        normalized.tenantId(),
        ExceptionModels.PriceMutationGuardDecision.ALLOWED,
        "No configured guarded price fields were mutated",
        List.of(),
        null,
        replayHash,
        normalized.correlationId()
      );
    }

    if (isAuthorizedPricingCommand(normalized, normalizedPolicy)) {
      String replayHash = hash(String.join("|",
        normalized.tenantId().toString(), normalized.targetType().name(), normalized.quoteId(),
        guardedMutations.toString(), normalized.authorizedCommandRef().commandType(),
        normalized.authorizedCommandRef().approvedAdjustmentRef(), normalized.ledgerHashExpectation().expectedLedgerHash(),
        normalizedPolicy.policyVersionId(), "ALLOWED_AUTHORIZED_COMMAND"
      ));
      return new ExceptionModels.PriceMutationGuardResponse(
        normalized.tenantId(),
        ExceptionModels.PriceMutationGuardDecision.ALLOWED,
        "Configured governed pricing command authorized the guarded mutation",
        List.of(),
        null,
        replayHash,
        normalized.correlationId()
      );
    }

    String denialReason = denialReason(normalized, normalizedPolicy);
    String payloadHash = priceMutationPayloadHash(normalized, guardedMutations);
    var replayed = repository.findManualPriceEditAttemptByIdempotencyKey(normalized.tenantId(), normalized.idempotencyKey());
    if (replayed.isPresent()) {
      if (!replayed.get().payloadHash().equals(payloadHash) || !replayed.get().denialReason().equals(denialReason)) {
        throw new ExceptionServiceException(
          "IDEMPOTENCY_CONFLICT",
          "Idempotency-Key already exists for a different manual price edit attempt"
        );
      }
      return toPriceMutationBlockedResponse(replayed.get());
    }

    String eventHash = hash(String.join("|",
      normalized.tenantId().toString(), normalized.targetType().name(), normalized.quoteId(),
      Objects.toString(normalized.lockId(), ""), guardedMutations.toString(), payloadHash, denialReason,
      normalizedPolicy.policyVersionId(), normalized.actorId()
    ));
    return toPriceMutationBlockedResponse(repository.recordManualPriceEditAttempt(
      normalized,
      guardedMutations,
      payloadHash,
      denialReason,
      normalizedPolicy.policyVersionId(),
      eventHash
    ));
  }

  public ExceptionModels.BlockedMutationEvidence manualPriceEditAttemptStatus(UUID tenantId, String attemptId) {
    requireTenant(tenantId);
    requireText(attemptId, "VALIDATION_FAILED", "attemptId is required");
    return repository.findManualPriceEditAttemptById(tenantId, attemptId.trim())
      .map(ExceptionService::toBlockedEvidence)
      .orElseThrow(() -> new ExceptionServiceException(
        "NOT_FOUND",
        "Unknown manual price edit attempt id for tenant scope: " + attemptId
      ));
  }

  public ExceptionModels.ConcessionMonitoringAlertResponse monitorConcessionPattern(
    ExceptionModels.ConcessionMonitoringSignalCommand command,
    ExceptionModels.MonitoringPolicyVersion policy
  ) {
    validateMonitoringCommand(command);
    validateMonitoringPolicy(policy, command.signalType());

    String signalHash = monitoringSignalHash(command, policy);
    var replayed = repository.findMonitoringSignalByIdempotencyKey(command.tenantId(), command.idempotencyKey());
    if (replayed.isPresent()) {
      if (!replayed.get().signalHash().equals(signalHash)) {
        throw new ExceptionServiceException(
          "IDEMPOTENCY_CONFLICT",
          "Idempotency-Key already exists for a different monitoring signal"
        );
      }
      return repository.findMonitoringAlertBySignalId(replayed.get().signalId())
        .map(ExceptionService::toMonitoringAlertResponse)
        .orElseThrow(() -> new ExceptionServiceException(
          "MONITORING_ALERT_NOT_FOUND",
          "monitoring signal replay has no alert record"
        ));
    }

    ExceptionModels.MonitoringSignalRecord signal = repository.recordMonitoringSignal(
      normalizeMonitoringCommand(command),
      normalizeMonitoringPolicy(policy),
      signalHash
    );
    boolean cohortSuppressed = shouldSuppressCohort(command, policy);
    String safeCohortRef = cohortSuppressed || !policy.fairnessCohortViewAuthorized()
      ? null
      : trimToNull(command.fairnessCohortRef());
    ExceptionModels.AlertStatus status = cohortSuppressed
      ? ExceptionModels.AlertStatus.SUPPRESSED
      : ExceptionModels.AlertStatus.OPEN;
    Map<String, String> evidenceSnapshot = monitoringEvidenceSnapshot(command, policy, signalHash, cohortSuppressed);
    String evidenceHash = hash(evidenceSnapshot.toString());
    String replayHash = hash(String.join("|",
      signal.signalHash(),
      evidenceHash,
      status.name(),
      Objects.toString(safeCohortRef, ""),
      String.valueOf(cohortSuppressed)
    ));

    return toMonitoringAlertResponse(repository.raiseMonitoringAlert(
      signal,
      normalizeMonitoringPolicy(policy),
      status,
      evidenceSnapshot,
      safeCohortRef,
      cohortSuppressed,
      evidenceHash,
      replayHash
    ));
  }

  public ExceptionModels.RiskMonitoringEventEnvelope publishRiskMonitoringEvent(
    ExceptionModels.MapRiskMonitoringEventCommand command,
    ExceptionModels.RiskEventMappingVersion mappingVersion,
    boolean replayFlag
  ) {
    validateRiskMonitoringEventCommand(command);
    validateRiskEventMappingVersion(mappingVersion);
    ExceptionModels.RiskEventMappingRule rule = mappingVersion.mappings().stream()
      .filter(candidate -> candidate.sourceEventType() == command.sourceEventType())
      .findFirst()
      .orElseThrow(() -> new ExceptionServiceException(
        "POLICY_NOT_SATISFIED",
        "published risk event mapping version has no rule for source event type"
      ));
    validateRiskEventMappingRule(rule);

    Map<String, String> payload = normalizedMap(command.payload());
    Map<String, String> sourceRefs = normalizedMap(command.sourceRefs());
    List<String> prohibitedFields = normalizedProhibitedFields(mappingVersion.prohibitedPayloadFields());
    if (containsRawPiiToken(payload) || containsProhibitedPayloadField(payload, prohibitedFields)) {
      throw new ExceptionServiceException("RAW_PII_NOT_ALLOWED", "risk monitoring payload must contain non-PII refs only");
    }
    ExceptionModels.RiskMonitoringEventStatus status = rule.suppressionEligible()
      && mappingVersion.suppressedSignalTypes() != null
      && mappingVersion.suppressedSignalTypes().contains(rule.signalType())
        ? ExceptionModels.RiskMonitoringEventStatus.SUPPRESSED
        : ExceptionModels.RiskMonitoringEventStatus.READY;
    String payloadHash = hash(payload.toString() + "|" + sourceRefs.toString());
    String requestHash = hash(String.join("|",
      command.tenantId().toString(), command.sourceEventType().name(), command.sourceEventId().trim(),
      command.sourceService().trim(), command.riskSubjectRef().trim(), sourceRefs.toString(), payload.toString(),
      mappingVersion.mappingVersionId().trim(), rule.signalType().name(), rule.severity().name(), status.name(),
      String.valueOf(replayFlag)
    ));

    var replayed = repository.findRiskMonitoringEventByIdempotencyKey(command.tenantId(), command.idempotencyKey().trim());
    if (replayed.isPresent()) {
      if (!replayed.get().requestHash().equals(requestHash)) {
        throw new ExceptionServiceException(
          "IDEMPOTENCY_CONFLICT",
          "Idempotency-Key already exists for a different risk monitoring event"
        );
      }
      return toRiskMonitoringEventEnvelope(replayed.get());
    }
    var duplicateSource = repository.findRiskMonitoringEventBySourceEvent(command.tenantId(), command.sourceEventId().trim());
    if (duplicateSource.isPresent()) {
      if (!duplicateSource.get().requestHash().equals(requestHash)) {
        throw new ExceptionServiceException(
          "IDEMPOTENCY_CONFLICT",
          "source event already mapped to a different risk monitoring event"
        );
      }
      return toRiskMonitoringEventEnvelope(duplicateSource.get());
    }

    String topic = mappingVersion.topic().trim();
    String riskEventId = "RME-" + hash(command.tenantId() + "|" + command.sourceEventId().trim()).substring(0, 16);
    String eventKey = "SUBJECT".equalsIgnoreCase(mappingVersion.partitionStrategy())
      ? command.tenantId() + ":" + command.riskSubjectRef().trim()
      : command.tenantId() + ":" + riskEventId;
    Map<String, String> headers = riskEventHeaders(command, mappingVersion, rule, riskEventId, replayFlag);
    Map<String, String> eventPayload = riskEventPayload(command, rule, status, sourceRefs, payloadHash);
    Map<String, String> redactionManifest = riskEventRedactionManifest(prohibitedFields, payload);
    return toRiskMonitoringEventEnvelope(repository.recordRiskMonitoringEvent(
      normalizeRiskEventCommand(command),
      rule,
      status,
      topic,
      eventKey,
      headers,
      eventPayload,
      payloadHash,
      redactionManifest,
      mappingVersion.mappingVersionId().trim(),
      replayFlag,
      requestHash
    ));
  }

  public ExceptionModels.ConcessionAlertDispositionResponse dispositionMonitoringAlert(
    ExceptionModels.ConcessionAlertDispositionCommand command
  ) {
    validateDispositionCommand(command);
    var replayed = repository.findAlertDispositionByIdempotencyKey(command.tenantId(), command.idempotencyKey());
    if (replayed.isPresent()) {
      if (!dispositionMatchesExisting(command, replayed.get())) {
        throw new ExceptionServiceException(
          "IDEMPOTENCY_CONFLICT",
          "Idempotency-Key already exists for a different alert disposition"
        );
      }
      return toDispositionResponse(replayed.get());
    }

    ExceptionModels.MonitoringAlertRecord alert = repository
      .findMonitoringAlertById(command.tenantId(), command.alertId())
      .orElseThrow(() -> new ExceptionServiceException(
        "MONITORING_ALERT_NOT_FOUND",
        "Unknown concession monitoring alert id for tenant scope: " + command.alertId()
      ));
    if (alert.status() == ExceptionModels.AlertStatus.CLOSED) {
      throw new ExceptionServiceException("ALERT_ALREADY_CLOSED", "closed monitoring alerts cannot be dispositioned");
    }
    ExceptionModels.AlertStatus newStatus = dispositionStatus(command.decision());
    String commentRedacted = redactNarrative(command.comment());
    String dispositionHash = hash(String.join("|",
      command.tenantId().toString(),
      command.alertId(),
      command.decision().name(),
      command.reasonCode().trim(),
      commentRedacted,
      command.actorId().trim(),
      alert.status().name(),
      String.valueOf(alert.version())
    ));
    return toDispositionResponse(repository.dispositionMonitoringAlert(
      alert,
      normalizeDispositionCommand(command),
      newStatus,
      commentRedacted,
      dispositionHash
    ));
  }

  public ExceptionModels.ExceptionHistoryTimeline reconstructExceptionHistory(
    ExceptionModels.ExceptionHistorySearchRequest request
  ) {
    ExceptionModels.ExceptionHistorySearchRequest normalized = normalizeHistorySearch(request, "exception_history.view");
    boolean rawJsonAllowed = normalized.includeRawJson() && normalized.permissions().contains("exception_history.raw_json");
    boolean evidenceAllowed = normalized.permissions().contains("exception_history.evidence");
    List<ExceptionModels.TimelineEvent> events = new ArrayList<>();

    List<String> concessionIds = repository.concessionRequests().stream()
      .filter(record -> matchesConcession(record, normalized))
      .map(ExceptionModels.PricingConcessionRequestRecord::concessionRequestId)
      .sorted()
      .toList();
    Set<String> concessionIdSet = new HashSet<>(concessionIds);

    repository.exceptionRequests().stream()
      .filter(record -> matchesLegacyException(record, normalized))
      .map(record -> legacyExceptionTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    repository.concessionRequests().stream()
      .filter(record -> concessionIdSet.contains(record.concessionRequestId()))
      .map(record -> concessionTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    repository.approvalDecisions().stream()
      .filter(record -> concessionIdSet.contains(record.concessionRequestId()) || matchesActorOrCorrelation(record.actorId(), record.correlationId(), normalized))
      .map(record -> approvalTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    repository.concessionApplications().stream()
      .filter(record -> concessionIdSet.contains(record.concessionRequestId()) || matchesApplication(record, normalized))
      .map(record -> applicationTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    repository.eligibilityExceptionRequests().stream()
      .filter(record -> concessionIdSet.contains(record.relatedConcessionRequestId()) || matchesEligibility(record, normalized))
      .map(record -> eligibilityTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    List<ExceptionModels.MonitoringSignalRecord> matchingSignals = repository.monitoringSignals().stream()
      .filter(record -> concessionIdSet.contains(record.concessionRequestId()) || matchesMonitoringSignal(record, normalized))
      .toList();
    Set<String> signalIds = matchingSignals.stream().map(ExceptionModels.MonitoringSignalRecord::signalId).collect(java.util.stream.Collectors.toSet());
    matchingSignals.stream()
      .map(record -> monitoringSignalTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    Set<String> alertIds = new HashSet<>();
    repository.monitoringAlerts().stream()
      .filter(record -> signalIds.contains(record.signalId()) || matchesActorOrCorrelation(null, record.correlationId(), normalized))
      .peek(record -> alertIds.add(record.alertId()))
      .map(record -> monitoringAlertTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    repository.alertDispositions().stream()
      .filter(record -> alertIds.contains(record.alertId()) || matchesActorOrCorrelation(record.actorId(), record.correlationId(), normalized))
      .map(record -> alertDispositionTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    repository.manualPriceEditAttempts().stream()
      .filter(record -> matchesManualPriceEdit(record, normalized))
      .map(record -> manualPriceEditTimelineEvent(record, rawJsonAllowed, evidenceAllowed))
      .forEach(events::add);

    List<ExceptionModels.TimelineEvent> ordered = events.stream()
      .sorted(Comparator
        .comparing(ExceptionModels.TimelineEvent::occurredAt)
        .thenComparingInt(ExceptionModels.TimelineEvent::aggregateVersion)
        .thenComparing(ExceptionModels.TimelineEvent::eventId))
      .toList();
    ExceptionModels.VersionGraph versionGraph = buildVersionGraph(ordered);
    String projectionHash = hash(ordered.toString() + "|" + versionGraph.graphHash());
    ExceptionModels.ExceptionHistoryTimeline timeline = new ExceptionModels.ExceptionHistoryTimeline(
      normalized.tenantId(),
      normalized.subjectType(),
      normalized.subjectId(),
      ordered,
      versionGraph,
      projectionHash,
      "AUDIT-HISTORY-" + projectionHash.substring(0, 16),
      normalized.correlationId(),
      Instant.now()
    );
    repository.saveHistoryProjection(timeline);
    repository.recordHistoryAudit(
      timeline.tenantId(),
      "EXCEPTION_HISTORY_SEARCHED",
      timeline.subjectType(),
      timeline.subjectId(),
      normalized.actorId(),
      "history reconstruction search",
      timeline.projectionHash(),
      timeline.correlationId()
    );
    return timeline;
  }

  public ExceptionModels.ExceptionHistoryReplayResult replayExceptionHistory(
    ExceptionModels.ExceptionHistorySearchRequest request,
    String expectedProjectionHash,
    List<String> historicalConfigVersionIds
  ) {
    ExceptionModels.ExceptionHistorySearchRequest normalized = normalizeHistorySearch(request, "exception_history.replay");
    ExceptionModels.ExceptionHistoryTimeline timeline = reconstructExceptionHistory(normalized);
    String expected = isBlank(expectedProjectionHash) ? timeline.projectionHash() : expectedProjectionHash.trim();
    ExceptionModels.ExceptionHistoryReplayStatus status = timeline.projectionHash().equals(expected)
      ? ExceptionModels.ExceptionHistoryReplayStatus.MATCH
      : ExceptionModels.ExceptionHistoryReplayStatus.MISMATCH;
    List<String> configVersions = historicalConfigVersionIds == null || historicalConfigVersionIds.isEmpty()
      ? timeline.versionGraph().configVersions().values().stream().sorted().toList()
      : historicalConfigVersionIds.stream().filter(value -> !isBlank(value)).map(String::trim).sorted().toList();
    String replayHash = hash(timeline.projectionHash() + "|" + expected + "|" + configVersions.toString());
    ExceptionModels.ExceptionHistoryReplayResult replay = new ExceptionModels.ExceptionHistoryReplayResult(
      normalized.tenantId(),
      "REPLAY-" + replayHash.substring(0, 16),
      normalized.subjectType(),
      normalized.subjectId(),
      timeline.versionGraph().eventIds(),
      configVersions,
      expected,
      timeline.projectionHash(),
      status,
      status == ExceptionModels.ExceptionHistoryReplayStatus.MATCH ? "NONE" : "PROJECTION_HASH_MISMATCH",
      replayHash,
      "AUDIT-REPLAY-" + replayHash.substring(0, 16),
      "ExceptionHistoryReplayCompleted.v1",
      normalized.correlationId(),
      Instant.now()
    );
    repository.saveHistoryReplay(replay);
    repository.recordHistoryAudit(
      replay.tenantId(),
      "EXCEPTION_HISTORY_REPLAYED",
      replay.subjectType(),
      replay.subjectId(),
      normalized.actorId(),
      replay.status().name(),
      replay.replayHash(),
      replay.correlationId()
    );
    return replay;
  }

  public ExceptionModels.ExceptionHistoryExportPacket exportExceptionHistory(
    ExceptionModels.ExceptionHistorySearchRequest request,
    boolean includeEvidenceRefs,
    Instant expiresAt
  ) {
    ExceptionModels.ExceptionHistorySearchRequest normalized = normalizeHistorySearch(request, "exception_history.export");
    ExceptionModels.ExceptionHistoryTimeline timeline = reconstructExceptionHistory(normalized);
    ExceptionModels.ExceptionHistoryReplayResult replay = replayExceptionHistory(normalized, timeline.projectionHash(), List.of());
    boolean rawJsonAllowed = normalized.includeRawJson() && normalized.permissions().contains("exception_history.raw_json");
    boolean evidenceAllowed = includeEvidenceRefs && normalized.permissions().contains("exception_history.evidence");
    List<String> excludedFields = new ArrayList<>();
    if (!rawJsonAllowed) {
      excludedFields.add("raw_event_json");
    }
    if (!evidenceAllowed) {
      excludedFields.add("evidence_refs");
    }
    String redactionMode = excludedFields.isEmpty() ? "FULL_AUTHORIZED" : "LEAST_PRIVILEGE_REDACTED";
    String manifestHash = hash(timeline.projectionHash() + "|" + replay.replayHash() + "|" + redactionMode + "|" + excludedFields);
    ExceptionModels.ExceptionHistoryExportManifest manifest = new ExceptionModels.ExceptionHistoryExportManifest(
      "EXPORT-" + manifestHash.substring(0, 16),
      manifestHash,
      "signed-sha256:" + hash("exception-service-export|" + manifestHash),
      redactionMode,
      normalized.permissions(),
      List.copyOf(excludedFields),
      "exception-history://exports/" + manifestHash.substring(0, 16),
      expiresAt == null ? Instant.now().plusSeconds(86400) : expiresAt
    );
    ExceptionModels.ExceptionHistoryExportPacket export = new ExceptionModels.ExceptionHistoryExportPacket(
      normalized.tenantId(),
      normalized.subjectType(),
      normalized.subjectId(),
      timeline,
      replay,
      manifest,
      "AUDIT-EXPORT-" + manifestHash.substring(0, 16),
      "ExceptionHistoryExportCreated.v1",
      normalized.correlationId(),
      Instant.now()
    );
    repository.saveHistoryExport(export);
    repository.recordHistoryAudit(
      export.tenantId(),
      "EXCEPTION_HISTORY_EXPORTED",
      export.subjectType(),
      export.subjectId(),
      normalized.actorId(),
      export.manifest().redactionMode(),
      export.manifest().manifestHash(),
      export.correlationId()
    );
    return export;
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
      record.expiration(),
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

  private static ExceptionModels.EligibilityExceptionRequestResponse toEligibilityExceptionResponse(
    ExceptionModels.EligibilityExceptionRequestRecord record
  ) {
    return new ExceptionModels.EligibilityExceptionRequestResponse(
      record.tenantId(),
      record.exceptionRequestId(),
      record.quoteId(),
      record.scenarioId(),
      record.lockId(),
      record.findingRef(),
      record.exceptionScope(),
      record.reasonCode(),
      record.compensatingFactors(),
      record.evidenceRefs(),
      record.desiredExpiration(),
      record.relatedConcessionRequestId(),
      record.status(),
      record.policyVersionId(),
      record.authorityMatrixVersionId(),
      record.approvalRouteHash(),
      record.originalResultHash(),
      record.auditRef(),
      record.outboxEventType(),
      record.eventHash(),
      record.idempotencyKey(),
      record.actorId(),
      record.correlationId(),
      record.version(),
      record.createdAt(),
      record.updatedAt()
    );
  }

  private static ExceptionModels.CreateEligibilityExceptionRequest normalizeEligibilityExceptionRequest(
    ExceptionModels.CreateEligibilityExceptionRequest request
  ) {
    ExceptionModels.EligibilityFindingRef findingRef = request.findingRef();
    return new ExceptionModels.CreateEligibilityExceptionRequest(
      request.tenantId(),
      request.quoteId().trim(),
      request.scenarioId().trim(),
      trimToNull(request.lockId()),
      new ExceptionModels.EligibilityFindingRef(
        findingRef.eligibilityResultId().trim(),
        findingRef.findingId().trim(),
        findingRef.ruleCode().trim(),
        findingRef.ruleVersionId().trim(),
        findingRef.severity().trim(),
        findingRef.exceptionable(),
        findingRef.originalResultHash().trim()
      ),
      new ExceptionModels.EligibilityExceptionScope(
        request.exceptionScope().scopeType().trim(),
        normalizedMap(request.exceptionScope().attributes())
      ),
      request.reasonCode().trim(),
      request.narrative(),
      request.compensatingFactors() == null ? List.of() : request.compensatingFactors().stream()
        .map(factor -> new ExceptionModels.CompensatingFactor(factor.factorType().trim(), factor.description().trim()))
        .toList(),
      request.evidenceRefs() == null ? List.of() : request.evidenceRefs().stream()
        .map(ref -> new ExceptionModels.EligibilityExceptionEvidenceRef(
          ref.evidenceUri().trim(),
          ref.evidenceType().trim(),
          ref.checksum().trim(),
          ref.uploadedBy().trim()
        ))
        .toList(),
      trimToNull(request.desiredExpiration()),
      trimToNull(request.relatedConcessionRequestId()),
      request.actorId().trim(),
      request.idempotencyKey().trim(),
      request.correlationId().trim()
    );
  }

  private static ExceptionModels.EligibilityExceptionPolicy normalizeEligibilityExceptionPolicy(
    ExceptionModels.EligibilityExceptionPolicy policy
  ) {
    return new ExceptionModels.EligibilityExceptionPolicy(
      policy.policyVersionId().trim(),
      policy.authorityMatrixVersionId().trim(),
      policy.findingExceptionable(),
      policy.quoteStateAllowsRequest(),
      policy.requiredEvidence(),
      new ExceptionModels.ApprovalRouteSnapshot(
        policy.approvalRouteSnapshot().authorityMatrixVersionId().trim(),
        policy.approvalRouteSnapshot().approverGroups().stream().map(String::trim).filter(value -> !value.isEmpty()).toList(),
        trimToNull(policy.approvalRouteSnapshot().sla()),
        policy.approvalRouteSnapshot().ambiguous()
      )
    );
  }

  private static void validateEligibilityExceptionRequest(
    ExceptionModels.CreateEligibilityExceptionRequest request,
    ExceptionModels.EligibilityExceptionPolicy policy
  ) {
    if (request == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "eligibility exception request is required");
    }
    requireTenant(request.tenantId());
    requireText(request.quoteId(), "QUOTE_STATE_INVALID", "quoteId is required");
    requireText(request.scenarioId(), "VALIDATION_FAILED", "scenarioId is required");
    requireText(request.reasonCode(), "POLICY_NOT_SATISFIED", "configured reasonCode is required");
    requireText(request.actorId(), "FORBIDDEN", "actorId is required");
    requireText(request.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(request.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    validateEligibilityFinding(request.findingRef());
    validateExceptionScope(request.exceptionScope());
    validateEligibilityPolicy(policy);
    validateCompensatingFactors(request.compensatingFactors());
    validateEligibilityEvidence(request.evidenceRefs());
    if (!request.findingRef().exceptionable() || !policy.findingExceptionable()) {
      throw new ExceptionServiceException("FINDING_NOT_EXCEPTIONABLE", "configured eligibility finding is not exceptionable");
    }
    if (!policy.quoteStateAllowsRequest()) {
      throw new ExceptionServiceException("QUOTE_STATE_INVALID", "quote state does not allow eligibility exception request");
    }
    if (policy.requiredEvidence() && (request.evidenceRefs() == null || request.evidenceRefs().isEmpty())) {
      throw new ExceptionServiceException("REQUIRED_EVIDENCE_MISSING", "configured evidence is required");
    }
  }

  private static void validateEligibilityFinding(ExceptionModels.EligibilityFindingRef findingRef) {
    if (findingRef == null) {
      throw new ExceptionServiceException("ELIGIBILITY_FINDING_NOT_FOUND", "eligibility finding reference is required");
    }
    requireText(findingRef.eligibilityResultId(), "ELIGIBILITY_FINDING_NOT_FOUND", "eligibilityResultId is required");
    requireText(findingRef.findingId(), "ELIGIBILITY_FINDING_NOT_FOUND", "findingId is required");
    requireText(findingRef.ruleCode(), "ELIGIBILITY_FINDING_NOT_FOUND", "ruleCode is required");
    requireText(findingRef.ruleVersionId(), "ELIGIBILITY_FINDING_NOT_FOUND", "ruleVersionId is required");
    requireText(findingRef.severity(), "POLICY_NOT_SATISFIED", "configured severity is required");
    requireText(findingRef.originalResultHash(), "ELIGIBILITY_FINDING_NOT_FOUND", "originalResultHash is required");
  }

  private static void validateExceptionScope(ExceptionModels.EligibilityExceptionScope exceptionScope) {
    if (exceptionScope == null || isBlank(exceptionScope.scopeType())
      || exceptionScope.attributes() == null || exceptionScope.attributes().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured exception scope is required");
    }
  }

  private static void validateEligibilityPolicy(ExceptionModels.EligibilityExceptionPolicy policy) {
    if (policy == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "eligibility exception policy is required");
    }
    requireText(policy.policyVersionId(), "POLICY_NOT_SATISFIED", "policyVersionId is required");
    requireText(policy.authorityMatrixVersionId(), "POLICY_NOT_SATISFIED", "authorityMatrixVersionId is required");
    validateApprovalRoute(policy.approvalRouteSnapshot());
    if (!policy.authorityMatrixVersionId().trim().equals(policy.approvalRouteSnapshot().authorityMatrixVersionId().trim())) {
      throw new ExceptionServiceException("AUTHORITY_ROUTE_UNRESOLVED", "authority matrix version must match route snapshot");
    }
  }

  private static void validateCompensatingFactors(List<ExceptionModels.CompensatingFactor> factors) {
    if (factors == null) {
      return;
    }
    factors.forEach(factor -> {
      if (factor == null || isBlank(factor.factorType()) || isBlank(factor.description())) {
        throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured compensating factors must include type and description");
      }
    });
  }

  private static void validateEligibilityEvidence(List<ExceptionModels.EligibilityExceptionEvidenceRef> evidenceRefs) {
    if (evidenceRefs == null) {
      return;
    }
    evidenceRefs.forEach(ref -> {
      if (ref == null || isBlank(ref.evidenceUri()) || isBlank(ref.evidenceType()) || isBlank(ref.checksum())
        || isBlank(ref.uploadedBy())) {
        throw new ExceptionServiceException("REQUIRED_EVIDENCE_MISSING", "evidence refs must include uri, type, checksum, and uploader");
      }
    });
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

  private static ExceptionModels.ApplyApprovedConcessionRequest normalizeApplyRequest(
    ExceptionModels.ApplyApprovedConcessionRequest command
  ) {
    ExceptionModels.ApplicationTarget target = command.target();
    return new ExceptionModels.ApplyApprovedConcessionRequest(
      command.tenantId(),
      command.concessionRequestId().trim(),
      new ExceptionModels.ApplicationTarget(
        target.targetType(),
        target.quoteId().trim(),
        isBlank(target.lockId()) ? null : target.lockId().trim(),
        target.currentQuoteSnapshotHash().trim(),
        isBlank(target.currentLockState()) ? null : target.currentLockState().trim()
      ),
      command.expectedRequestVersion(),
      command.expectedQuoteSnapshotHash().trim(),
      command.expectedLedgerHash().trim(),
      command.pricingRuleVersionId().trim(),
      command.policyVersionId().trim(),
      new ExceptionModels.ApplicationPrecedence(
        command.precedence().precedenceConfigVersionId().trim(),
        command.precedence().scale(),
        command.precedence().roundingMode().trim()
      ),
      command.eligibilityExceptionsResolved(),
      command.actorId().trim(),
      command.idempotencyKey().trim(),
      command.correlationId().trim()
    );
  }

  private static void validateApplyRequest(ExceptionModels.ApplyApprovedConcessionRequest command) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "apply approved concession request is required");
    }
    requireTenant(command.tenantId());
    requireText(command.concessionRequestId(), "VALIDATION_FAILED", "concessionRequestId is required");
    requireText(command.expectedQuoteSnapshotHash(), "VALIDATION_FAILED", "expectedQuoteSnapshotHash is required");
    requireText(command.expectedLedgerHash(), "VALIDATION_FAILED", "expectedLedgerHash is required");
    requireText(command.pricingRuleVersionId(), "POLICY_NOT_SATISFIED", "pricingRuleVersionId is required");
    requireText(command.policyVersionId(), "POLICY_NOT_SATISFIED", "policyVersionId is required");
    requireText(command.actorId(), "FORBIDDEN", "actorId is required");
    requireText(command.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    validateTarget(command.target());
    validatePrecedence(command.precedence());
  }

  private static void validateTarget(ExceptionModels.ApplicationTarget target) {
    if (target == null || target.targetType() == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "application target is required");
    }
    requireText(target.quoteId(), "VALIDATION_FAILED", "quoteId is required");
    requireText(target.currentQuoteSnapshotHash(), "VALIDATION_FAILED", "currentQuoteSnapshotHash is required");
    if (target.targetType() == ExceptionModels.ApplicationTargetType.LOCK) {
      requireText(target.lockId(), "LOCK_STATE_INVALID", "lockId is required for lock concession application");
      requireText(target.currentLockState(), "LOCK_STATE_INVALID", "currentLockState is required for lock concession application");
    }
  }

  private static void validatePrecedence(ExceptionModels.ApplicationPrecedence precedence) {
    if (precedence == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "application precedence config is required");
    }
    requireText(precedence.precedenceConfigVersionId(), "POLICY_NOT_SATISFIED", "precedenceConfigVersionId is required");
    requireText(precedence.roundingMode(), "POLICY_NOT_SATISFIED", "roundingMode is required");
    if (precedence.scale() < 0) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured pricing scale must be non-negative");
    }
  }

  private static void validateApplyAgainstConcession(
    ExceptionModels.ApplyApprovedConcessionRequest command,
    ExceptionModels.PricingConcessionRequestRecord concession
  ) {
    if (concession.status() != ExceptionModels.ConcessionRequestStatus.APPROVED_PENDING_APPLICATION) {
      throw new ExceptionServiceException("REQUEST_NOT_APPROVED", "concession request must be fully approved before application");
    }
    if (command.expectedRequestVersion() != concession.version()) {
      throw new ExceptionServiceException("VERSION_CONFLICT", "expectedRequestVersion does not match current version");
    }
    if (!concession.quoteId().equals(command.target().quoteId())) {
      throw new ExceptionServiceException("QUOTE_HASH_CHANGED", "application target quote does not match concession request");
    }
    if (!concession.quoteSnapshotHash().equals(command.expectedQuoteSnapshotHash())
      || !command.expectedQuoteSnapshotHash().equals(command.target().currentQuoteSnapshotHash())) {
      throw new ExceptionServiceException("QUOTE_HASH_CHANGED", "quote or lock snapshot hash changed since approval");
    }
    if (command.target().targetType() == ExceptionModels.ApplicationTargetType.LOCK
      && !Objects.equals(concession.lockId(), command.target().lockId())) {
      throw new ExceptionServiceException("LOCK_STATE_INVALID", "application target lock does not match concession request");
    }
    if (!command.eligibilityExceptionsResolved()) {
      throw new ExceptionServiceException(
        "ELIGIBILITY_EXCEPTION_UNRESOLVED",
        "eligibility exception dependencies must be resolved before application"
      );
    }
    if (isExpired(concession)) {
      throw new ExceptionServiceException("CONCESSION_EXPIRED", "approved concession is expired");
    }
  }

  private static boolean isExpired(ExceptionModels.PricingConcessionRequestRecord concession) {
    String expiration = concession.expiration();
    if (isBlank(expiration)) {
      return false;
    }
    try {
      return LocalDate.parse(expiration).isBefore(LocalDate.now());
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private static boolean applicationMatchesExisting(
    ExceptionModels.ApplyApprovedConcessionRequest command,
    ExceptionModels.ConcessionApplicationRecord existing
  ) {
    return existing.concessionRequestId().equals(command.concessionRequestId())
      && existing.targetType() == command.target().targetType()
      && existing.quoteId().equals(command.target().quoteId())
      && Objects.equals(existing.lockId(), command.target().lockId())
      && existing.beforePriceHash().equals(command.expectedLedgerHash())
      && existing.pricingRuleVersionId().equals(command.pricingRuleVersionId())
      && existing.policyVersionId().equals(command.policyVersionId())
      && existing.precedenceConfigVersionId().equals(command.precedence().precedenceConfigVersionId())
      && existing.scale() == command.precedence().scale()
      && existing.roundingMode().equals(command.precedence().roundingMode())
      && existing.appliedBy().equals(command.actorId());
  }

  private static String applyReplayHash(
    ExceptionModels.ApplyApprovedConcessionRequest command,
    ExceptionModels.PricingConcessionRequestRecord concession,
    String pricingLedgerEntryId,
    String afterPriceHash
  ) {
    return hash(String.join("|",
      concession.requestHash(),
      command.target().targetType().name(),
      command.target().quoteId(),
      Objects.toString(command.target().lockId(), ""),
      command.expectedLedgerHash(),
      afterPriceHash,
      pricingLedgerEntryId,
      command.pricingRuleVersionId(),
      command.policyVersionId(),
      command.precedence().precedenceConfigVersionId(),
      command.precedence().roundingMode(),
      String.valueOf(command.precedence().scale())
    ));
  }

  private static ExceptionModels.ConcessionApplicationResponse toApplicationResponse(
    ExceptionModels.ConcessionApplicationRecord record
  ) {
    return new ExceptionModels.ConcessionApplicationResponse(
      record.tenantId(),
      record.applicationId(),
      record.concessionRequestId(),
      record.targetType(),
      record.quoteId(),
      record.lockId(),
      record.status(),
      record.pricingLedgerEntryId(),
      record.beforePriceHash(),
      record.afterPriceHash(),
      record.pricingRuleVersionId(),
      record.policyVersionId(),
      record.precedenceConfigVersionId(),
      record.auditRef(),
      record.outboxEventType(),
      record.replayHash(),
      record.correlationId(),
      record.version(),
      record.appliedAt()
    );
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

  private static void validateGuardCommand(ExceptionModels.GuardPriceMutationCommand command) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "price mutation guard command is required");
    }
    requireTenant(command.tenantId());
    if (command.targetType() == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "targetType is required");
    }
    requireText(command.quoteId(), "VALIDATION_FAILED", "quoteId is required");
    requireText(command.sourceSurface(), "POLICY_NOT_SATISFIED", "configured sourceSurface is required");
    requireText(command.actorId(), "FORBIDDEN", "actorId is required");
    requireText(command.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    if (command.targetType() == ExceptionModels.PriceMutationTargetType.LOCK) {
      requireText(command.lockId(), "VALIDATION_FAILED", "lockId is required for lock price mutation checks");
    }
    if (command.fieldMutations() == null || command.fieldMutations().isEmpty()) {
      throw new ExceptionServiceException("PRICE_FIELD_READ_ONLY", "at least one price field mutation is required");
    }
    command.fieldMutations().forEach(mutation -> {
      if (mutation == null || isBlank(mutation.fieldName())) {
        throw new ExceptionServiceException("VALIDATION_FAILED", "fieldName is required for each price mutation");
      }
    });
  }

  private static ExceptionModels.GuardPriceMutationCommand normalizeGuardCommand(
    ExceptionModels.GuardPriceMutationCommand command
  ) {
    return new ExceptionModels.GuardPriceMutationCommand(
      command.tenantId(),
      command.targetType(),
      command.quoteId().trim(),
      trimToNull(command.lockId()),
      command.sourceSurface().trim(),
      command.fieldMutations().stream()
        .map(mutation -> new ExceptionModels.PriceFieldMutation(
          mutation.fieldName().trim(),
          trimToNull(mutation.previousValueHash()),
          trimToNull(mutation.proposedValueHash())
        ))
        .toList(),
      normalizeAuthorizedCommandRef(command.authorizedCommandRef()),
      normalizeLedgerHashExpectation(command.ledgerHashExpectation()),
      command.actorId().trim(),
      command.idempotencyKey().trim(),
      command.correlationId().trim()
    );
  }

  private static ExceptionModels.AuthorizedPricingCommandRef normalizeAuthorizedCommandRef(
    ExceptionModels.AuthorizedPricingCommandRef commandRef
  ) {
    if (commandRef == null) {
      return null;
    }
    return new ExceptionModels.AuthorizedPricingCommandRef(
      trimToNull(commandRef.commandType()),
      trimToNull(commandRef.commandId()),
      trimToNull(commandRef.workflowCapabilityRef()),
      trimToNull(commandRef.approvedAdjustmentRef())
    );
  }

  private static ExceptionModels.LedgerHashExpectation normalizeLedgerHashExpectation(
    ExceptionModels.LedgerHashExpectation expectation
  ) {
    if (expectation == null) {
      return null;
    }
    return new ExceptionModels.LedgerHashExpectation(
      trimToNull(expectation.expectedLedgerHash()),
      trimToNull(expectation.beforeQuoteHash()),
      trimToNull(expectation.beforeLockHash())
    );
  }

  private static ExceptionModels.PriceMutationGuardPolicyVersion normalizeGuardPolicy(
    ExceptionModels.PriceMutationGuardPolicyVersion policy
  ) {
    if (policy == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "published price mutation guard policy is required");
    }
    requireText(policy.policyVersionId(), "POLICY_NOT_SATISFIED", "policyVersionId is required");
    if (policy.status() != ExceptionModels.PriceMutationGuardPolicyStatus.PUBLISHED) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "price mutation guard policy must be published");
    }
    if (policy.guardedFields() == null || policy.guardedFields().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured guarded fields are required");
    }
    if (policy.allowedCommandTypes() == null || policy.allowedCommandTypes().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured allowed command types are required");
    }
    if (policy.workflowCapabilityRefs() == null || policy.workflowCapabilityRefs().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured workflow capability refs are required");
    }
    requireText(policy.effectiveFrom(), "POLICY_NOT_SATISFIED", "effectiveFrom is required");
    return new ExceptionModels.PriceMutationGuardPolicyVersion(
      policy.policyVersionId().trim(),
      policy.status(),
      normalizeTokenList(policy.guardedFields()),
      normalizeTokenList(policy.allowedCommandTypes()),
      normalizeTokenList(policy.workflowCapabilityRefs()),
      policy.effectiveFrom().trim()
    );
  }

  private static List<String> guardedMutationFields(
    List<ExceptionModels.PriceFieldMutation> mutations,
    List<String> guardedFields
  ) {
    Set<String> configured = new HashSet<>(guardedFields);
    return mutations.stream()
      .map(mutation -> mutation.fieldName().trim())
      .filter(configured::contains)
      .sorted()
      .distinct()
      .toList();
  }

  private static boolean isAuthorizedPricingCommand(
    ExceptionModels.GuardPriceMutationCommand command,
    ExceptionModels.PriceMutationGuardPolicyVersion policy
  ) {
    ExceptionModels.AuthorizedPricingCommandRef commandRef = command.authorizedCommandRef();
    ExceptionModels.LedgerHashExpectation ledger = command.ledgerHashExpectation();
    return commandRef != null
      && !isBlank(commandRef.commandType())
      && !isBlank(commandRef.workflowCapabilityRef())
      && !isBlank(commandRef.approvedAdjustmentRef())
      && ledger != null
      && !isBlank(ledger.expectedLedgerHash())
      && policy.allowedCommandTypes().contains(commandRef.commandType())
      && policy.workflowCapabilityRefs().contains(commandRef.workflowCapabilityRef());
  }

  private static String denialReason(
    ExceptionModels.GuardPriceMutationCommand command,
    ExceptionModels.PriceMutationGuardPolicyVersion policy
  ) {
    ExceptionModels.AuthorizedPricingCommandRef commandRef = command.authorizedCommandRef();
    if (commandRef == null || isBlank(commandRef.commandType())) {
      return "MANUAL_PRICE_EDIT_FORBIDDEN";
    }
    if (!policy.allowedCommandTypes().contains(commandRef.commandType())) {
      return "UNAUTHORIZED_BYPASS_ATTEMPT";
    }
    if (isBlank(commandRef.approvedAdjustmentRef())) {
      return "MISSING_APPROVED_ADJUSTMENT_REFERENCE";
    }
    if (command.ledgerHashExpectation() == null || isBlank(command.ledgerHashExpectation().expectedLedgerHash())) {
      return "LEDGER_HASH_REQUIRED";
    }
    return "UNAUTHORIZED_BYPASS_ATTEMPT";
  }

  private static String priceMutationPayloadHash(
    ExceptionModels.GuardPriceMutationCommand command,
    List<String> guardedMutations
  ) {
    return hash(String.join("|",
      command.tenantId().toString(), command.targetType().name(), command.quoteId(), Objects.toString(command.lockId(), ""),
      command.sourceSurface(), guardedMutations.toString(), command.fieldMutations().toString(), command.actorId()
    ));
  }

  private static ExceptionModels.PriceMutationGuardResponse toPriceMutationBlockedResponse(
    ExceptionModels.ManualPriceEditAttemptRecord record
  ) {
    ExceptionModels.BlockedMutationEvidence evidence = toBlockedEvidence(record);
    return new ExceptionModels.PriceMutationGuardResponse(
      record.tenantId(),
      ExceptionModels.PriceMutationGuardDecision.BLOCKED,
      "Configured guard blocked direct manual price mutation",
      List.of(record.denialReason()),
      evidence,
      hash(record.payloadHash() + "|" + record.eventHash() + "|" + record.denialReason()),
      record.correlationId()
    );
  }

  private static ExceptionModels.BlockedMutationEvidence toBlockedEvidence(
    ExceptionModels.ManualPriceEditAttemptRecord record
  ) {
    return new ExceptionModels.BlockedMutationEvidence(
      record.attemptId(),
      record.targetType(),
      record.sourceSurface(),
      record.quoteId(),
      record.lockId(),
      record.fieldNames(),
      record.payloadHash(),
      record.denialReason(),
      record.policyVersionId(),
      record.auditRef(),
      record.outboxEventType(),
      record.eventHash(),
      record.correlationId(),
      record.createdAt()
    );
  }

  private static List<String> normalizeTokenList(List<String> values) {
    return values.stream()
      .filter(value -> !isBlank(value))
      .map(String::trim)
      .distinct()
      .toList();
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

  private static void validateMonitoringCommand(ExceptionModels.ConcessionMonitoringSignalCommand command) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "concession monitoring signal is required");
    }
    requireTenant(command.tenantId());
    requireText(command.sourceEventId(), "VALIDATION_FAILED", "sourceEventId is required");
    requireText(command.concessionRequestId(), "VALIDATION_FAILED", "concessionRequestId is required");
    if (command.signalType() == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured signalType is required");
    }
    requireText(command.actorId(), "FORBIDDEN", "actorId is required");
    requireText(command.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    if (command.dimensions() == null || command.dimensions().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured non-PII dimensions are required");
    }
    if (command.measurements() == null || command.measurements().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured detector measurements are required");
    }
    if (containsRawPiiToken(command.dimensions()) || containsRawPiiToken(command.measurements())) {
      throw new ExceptionServiceException("RAW_PII_NOT_ALLOWED", "monitoring evidence must use non-PII refs or buckets");
    }
  }

  private static void validateRiskMonitoringEventCommand(ExceptionModels.MapRiskMonitoringEventCommand command) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "risk monitoring event command is required");
    }
    requireTenant(command.tenantId());
    if (command.sourceEventType() == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured sourceEventType is required");
    }
    requireText(command.sourceEventId(), "VALIDATION_FAILED", "sourceEventId is required");
    requireText(command.sourceService(), "POLICY_NOT_SATISFIED", "sourceService is required");
    requireText(command.riskSubjectRef(), "VALIDATION_FAILED", "riskSubjectRef is required");
    requireText(command.actorId(), "FORBIDDEN", "actorId is required");
    requireText(command.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    requireText(command.causationId(), "VALIDATION_FAILED", "causationId is required");
    requireText(command.traceId(), "VALIDATION_FAILED", "traceId is required");
    if (command.sourceRefs() == null || command.sourceRefs().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured sourceRefs are required");
    }
    if (command.payload() == null || command.payload().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured risk payload refs are required");
    }
  }

  private static void validateRiskEventMappingVersion(ExceptionModels.RiskEventMappingVersion mappingVersion) {
    if (mappingVersion == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "published risk event mapping version is required");
    }
    requireText(mappingVersion.mappingVersionId(), "POLICY_NOT_SATISFIED", "mappingVersionId is required");
    requireText(mappingVersion.topic(), "POLICY_NOT_SATISFIED", "topic is required");
    requireText(mappingVersion.eventVersion(), "POLICY_NOT_SATISFIED", "eventVersion is required");
    requireText(mappingVersion.partitionStrategy(), "POLICY_NOT_SATISFIED", "partitionStrategy is required");
    requireText(mappingVersion.approvedBy(), "POLICY_NOT_SATISFIED", "approvedBy is required");
    requireText(mappingVersion.publishedAt(), "POLICY_NOT_SATISFIED", "publishedAt is required");
    if (mappingVersion.status() != ExceptionModels.MonitoringPolicyStatus.PUBLISHED) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "risk event mapping version must be published");
    }
    if (mappingVersion.mappings() == null || mappingVersion.mappings().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "risk event mappings are required");
    }
  }

  private static void validateRiskEventMappingRule(ExceptionModels.RiskEventMappingRule rule) {
    if (rule.sourceEventType() == null || rule.signalType() == null || rule.severity() == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "risk event mapping rule must define source, signal, and severity");
    }
    requireText(rule.category(), "POLICY_NOT_SATISFIED", "category is required");
    requireText(rule.schemaVersion(), "POLICY_NOT_SATISFIED", "schemaVersion is required");
    requireText(rule.routingHint(), "POLICY_NOT_SATISFIED", "routingHint is required");
  }

  private static ExceptionModels.MapRiskMonitoringEventCommand normalizeRiskEventCommand(
    ExceptionModels.MapRiskMonitoringEventCommand command
  ) {
    return new ExceptionModels.MapRiskMonitoringEventCommand(
      command.tenantId(),
      command.sourceEventType(),
      command.sourceEventId().trim(),
      command.sourceService().trim(),
      command.riskSubjectRef().trim(),
      normalizedMap(command.sourceRefs()),
      normalizedMap(command.payload()),
      command.actorId().trim(),
      command.idempotencyKey().trim(),
      command.correlationId().trim(),
      command.causationId().trim(),
      command.traceId().trim()
    );
  }

  private static List<String> normalizedProhibitedFields(List<String> fields) {
    if (fields == null) {
      return List.of();
    }
    return fields.stream().filter(value -> !isBlank(value)).map(value -> value.trim().toLowerCase(Locale.ROOT)).sorted().toList();
  }

  private static boolean containsProhibitedPayloadField(Map<String, String> payload, List<String> prohibitedFields) {
    Set<String> payloadKeys = payload.keySet().stream().map(key -> key.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    return prohibitedFields.stream().anyMatch(payloadKeys::contains);
  }

  private static Map<String, String> riskEventHeaders(
    ExceptionModels.MapRiskMonitoringEventCommand command,
    ExceptionModels.RiskEventMappingVersion mappingVersion,
    ExceptionModels.RiskEventMappingRule rule,
    String riskEventId,
    boolean replayFlag
  ) {
    Map<String, String> headers = new TreeMap<>();
    headers.put("tenantId", command.tenantId().toString());
    headers.put("eventId", riskEventId);
    headers.put("eventType", "risk_monitoring_events.completed.v1");
    headers.put("eventVersion", mappingVersion.eventVersion().trim());
    headers.put("sourceService", command.sourceService().trim());
    headers.put("actorId", command.actorId().trim());
    headers.put("correlationId", command.correlationId().trim());
    headers.put("causationId", command.causationId().trim());
    headers.put("idempotencyKey", command.idempotencyKey().trim());
    headers.put("occurredAt", Instant.now().toString());
    headers.put("sourceEventId", command.sourceEventId().trim());
    headers.put("sourceEventType", command.sourceEventType().name());
    headers.put("schemaVersion", rule.schemaVersion().trim());
    headers.put("mappingVersionId", mappingVersion.mappingVersionId().trim());
    headers.put("traceId", command.traceId().trim());
    headers.put("replayFlag", String.valueOf(replayFlag));
    return headers;
  }

  private static Map<String, String> riskEventPayload(
    ExceptionModels.MapRiskMonitoringEventCommand command,
    ExceptionModels.RiskEventMappingRule rule,
    ExceptionModels.RiskMonitoringEventStatus status,
    Map<String, String> sourceRefs,
    String payloadHash
  ) {
    Map<String, String> payload = new TreeMap<>();
    payload.put("id", "RME-" + hash(command.tenantId() + "|" + command.sourceEventId().trim()).substring(0, 16));
    payload.put("tenantId", command.tenantId().toString());
    payload.put("status", status.name());
    payload.put("version", "1");
    payload.put("summary", command.sourceEventType().name() + " mapped to " + rule.signalType().name());
    payload.put("sourceRefs", sourceRefs.toString());
    payload.put("category", rule.category().trim());
    payload.put("routingHint", rule.routingHint().trim());
    payload.put("payloadHash", payloadHash);
    return payload;
  }

  private static Map<String, String> riskEventRedactionManifest(List<String> prohibitedFields, Map<String, String> payload) {
    Map<String, String> manifest = new TreeMap<>();
    manifest.put("mode", "NON_PII_REFS_ONLY");
    manifest.put("prohibitedFields", prohibitedFields.toString());
    manifest.put("payloadFields", payload.keySet().stream().sorted().toList().toString());
    manifest.put("rawPiiAllowed", "false");
    manifest.put("payloadHashAlgorithm", "SHA-256");
    return manifest;
  }

  private static void validateMonitoringPolicy(
    ExceptionModels.MonitoringPolicyVersion policy,
    ExceptionModels.MonitoringSignalType signalType
  ) {
    if (policy == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "published monitoring policy version is required");
    }
    requireText(policy.detectorId(), "POLICY_NOT_SATISFIED", "detectorId is required");
    requireText(policy.detectorVersionId(), "POLICY_NOT_SATISFIED", "detectorVersionId is required");
    requireText(policy.policyConfigRef(), "POLICY_NOT_SATISFIED", "policyConfigRef is required");
    requireText(policy.monitoringWindow(), "POLICY_NOT_SATISFIED", "monitoringWindow is required");
    if (policy.status() != ExceptionModels.MonitoringPolicyStatus.PUBLISHED) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "monitoring policy version must be published");
    }
    if (policy.severity() == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured alert severity is required");
    }
    if (policy.groupingDimensions() == null || policy.groupingDimensions().isEmpty()) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured grouping dimensions are required");
    }
    if (signalType == ExceptionModels.MonitoringSignalType.FAIRNESS_DISPARITY
      && (policy.minimumCellSize() == null || policy.minimumCellSize() < 0)) {
      throw new ExceptionServiceException(
        "POLICY_NOT_SATISFIED",
        "configured minimum cell-size policy is required for fairness monitoring"
      );
    }
  }

  private static ExceptionModels.ConcessionMonitoringSignalCommand normalizeMonitoringCommand(
    ExceptionModels.ConcessionMonitoringSignalCommand command
  ) {
    return new ExceptionModels.ConcessionMonitoringSignalCommand(
      command.tenantId(),
      command.sourceEventId().trim(),
      command.concessionRequestId().trim(),
      trimToNull(command.applicationId()),
      trimToNull(command.approvalDecisionId()),
      command.signalType(),
      normalizedMap(command.dimensions()),
      normalizedMap(command.measurements()),
      trimToNull(command.fairnessCohortRef()),
      command.fairnessCohortCellCount(),
      command.actorId().trim(),
      command.idempotencyKey().trim(),
      command.correlationId().trim()
    );
  }

  private static ExceptionModels.MonitoringPolicyVersion normalizeMonitoringPolicy(
    ExceptionModels.MonitoringPolicyVersion policy
  ) {
    return new ExceptionModels.MonitoringPolicyVersion(
      policy.detectorId().trim(),
      policy.detectorVersionId().trim(),
      policy.status(),
      policy.policyConfigRef().trim(),
      policy.severity(),
      policy.monitoringWindow().trim(),
      policy.groupingDimensions().stream().map(String::trim).filter(value -> !value.isEmpty()).toList(),
      policy.minimumCellSize(),
      policy.fairnessCohortViewAuthorized()
    );
  }

  private static ExceptionModels.ConcessionAlertDispositionCommand normalizeDispositionCommand(
    ExceptionModels.ConcessionAlertDispositionCommand command
  ) {
    return new ExceptionModels.ConcessionAlertDispositionCommand(
      command.tenantId(),
      command.alertId().trim(),
      command.decision(),
      command.reasonCode().trim(),
      command.comment(),
      command.actorId().trim(),
      command.idempotencyKey().trim(),
      command.correlationId().trim()
    );
  }

  private static void validateDispositionCommand(ExceptionModels.ConcessionAlertDispositionCommand command) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "alert disposition command is required");
    }
    requireTenant(command.tenantId());
    requireText(command.alertId(), "VALIDATION_FAILED", "alertId is required");
    if (command.decision() == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "alert disposition decision is required");
    }
    requireText(command.reasonCode(), "POLICY_NOT_SATISFIED", "configured disposition reasonCode is required");
    requireText(command.actorId(), "FORBIDDEN", "actorId is required");
    requireText(command.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
  }

  private static boolean shouldSuppressCohort(
    ExceptionModels.ConcessionMonitoringSignalCommand command,
    ExceptionModels.MonitoringPolicyVersion policy
  ) {
    return command.signalType() == ExceptionModels.MonitoringSignalType.FAIRNESS_DISPARITY
      && policy.minimumCellSize() != null
      && (command.fairnessCohortCellCount() == null
        || command.fairnessCohortCellCount() < policy.minimumCellSize());
  }

  private static Map<String, String> monitoringEvidenceSnapshot(
    ExceptionModels.ConcessionMonitoringSignalCommand command,
    ExceptionModels.MonitoringPolicyVersion policy,
    String signalHash,
    boolean cohortSuppressed
  ) {
    Map<String, String> evidence = new TreeMap<>();
    evidence.put("sourceEventId", command.sourceEventId().trim());
    evidence.put("concessionRequestId", command.concessionRequestId().trim());
    evidence.put("signalType", command.signalType().name());
    evidence.put("detectorId", policy.detectorId().trim());
    evidence.put("detectorVersionId", policy.detectorVersionId().trim());
    evidence.put("policyConfigRef", policy.policyConfigRef().trim());
    evidence.put("monitoringWindow", policy.monitoringWindow().trim());
    evidence.put("groupingDimensions", policy.groupingDimensions().toString());
    evidence.put("dimensionHash", hash(normalizedMap(command.dimensions()).toString()));
    evidence.put("measurementHash", hash(normalizedMap(command.measurements()).toString()));
    evidence.put("signalHash", signalHash);
    evidence.put("fairnessCohortSuppressed", String.valueOf(cohortSuppressed));
    if (command.fairnessCohortCellCount() != null) {
      evidence.put("fairnessCohortCellCount", String.valueOf(command.fairnessCohortCellCount()));
    }
    if (policy.minimumCellSize() != null) {
      evidence.put("minimumCellSizePolicy", String.valueOf(policy.minimumCellSize()));
    }
    return evidence;
  }

  private static String monitoringSignalHash(
    ExceptionModels.ConcessionMonitoringSignalCommand command,
    ExceptionModels.MonitoringPolicyVersion policy
  ) {
    return hash(String.join("|",
      command.tenantId().toString(),
      command.sourceEventId(),
      command.concessionRequestId(),
      Objects.toString(command.applicationId(), ""),
      Objects.toString(command.approvalDecisionId(), ""),
      command.signalType().name(),
      normalizedMap(command.dimensions()).toString(),
      normalizedMap(command.measurements()).toString(),
      Objects.toString(command.fairnessCohortRef(), ""),
      Objects.toString(command.fairnessCohortCellCount(), ""),
      policy.detectorId(),
      policy.detectorVersionId(),
      policy.policyConfigRef(),
      policy.monitoringWindow(),
      policy.groupingDimensions().toString()
    ));
  }

  private static ExceptionModels.AlertStatus dispositionStatus(ExceptionModels.AlertDispositionDecision decision) {
    return switch (decision) {
      case ACKNOWLEDGE -> ExceptionModels.AlertStatus.ACKNOWLEDGED;
      case FALSE_POSITIVE -> ExceptionModels.AlertStatus.FALSE_POSITIVE;
      case ESCALATE -> ExceptionModels.AlertStatus.ESCALATED;
      case REMEDIATE -> ExceptionModels.AlertStatus.REMEDIATED;
      case CLOSE -> ExceptionModels.AlertStatus.CLOSED;
    };
  }

  private static boolean dispositionMatchesExisting(
    ExceptionModels.ConcessionAlertDispositionCommand command,
    ExceptionModels.AlertDispositionRecord existing
  ) {
    return existing.alertId().equals(command.alertId())
      && existing.newStatus() == dispositionStatus(command.decision())
      && existing.reasonCode().equals(command.reasonCode())
      && existing.commentRedacted().equals(redactNarrative(command.comment()))
      && existing.actorId().equals(command.actorId());
  }

  private static ExceptionModels.ConcessionMonitoringAlertResponse toMonitoringAlertResponse(
    ExceptionModels.MonitoringAlertRecord record
  ) {
    return new ExceptionModels.ConcessionMonitoringAlertResponse(
      record.tenantId(),
      record.alertId(),
      record.signalId(),
      record.detectorId(),
      record.detectorVersionId(),
      record.severity(),
      record.status(),
      record.sourceEventIds(),
      record.evidenceSnapshot(),
      record.fairnessCohortRef(),
      record.fairnessCohortSuppressed(),
      record.auditRef(),
      record.outboxEventType(),
      record.evidenceHash(),
      record.replayHash(),
      record.correlationId(),
      record.version(),
      record.openedAt(),
      record.updatedAt()
    );
  }

  private static ExceptionModels.RiskMonitoringEventEnvelope toRiskMonitoringEventEnvelope(
    ExceptionModels.RiskMonitoringEventRecord record
  ) {
    return new ExceptionModels.RiskMonitoringEventEnvelope(
      record.tenantId(),
      record.riskEventId(),
      record.sourceEventId(),
      record.signalType(),
      record.severity(),
      record.status(),
      record.topic(),
      record.eventKey(),
      record.headers(),
      record.payload(),
      record.payloadHash(),
      record.redactionManifest(),
      record.mappingVersionId(),
      record.schemaVersion(),
      record.auditRef(),
      record.outboxEventType(),
      record.replayFlag(),
      record.createdAt()
    );
  }

  private static ExceptionModels.ConcessionAlertDispositionResponse toDispositionResponse(
    ExceptionModels.AlertDispositionRecord record
  ) {
    return new ExceptionModels.ConcessionAlertDispositionResponse(
      record.tenantId(),
      record.dispositionId(),
      record.alertId(),
      record.previousStatus(),
      record.newStatus(),
      record.reasonCode(),
      record.commentRedacted(),
      record.actorId(),
      record.auditRef(),
      record.outboxEventType(),
      record.dispositionHash(),
      record.correlationId(),
      record.alertVersion(),
      record.createdAt()
    );
  }

  private static ExceptionModels.CreateAuthorityMatrixDraftCommand normalizeAuthorityMatrixDraftCommand(
    ExceptionModels.CreateAuthorityMatrixDraftCommand command
  ) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "authority matrix draft command is required");
    }
    requireTenant(command.tenantId());
    requireText(command.versionLabel(), "VALIDATION_FAILED", "versionLabel is required");
    requireText(command.actorId(), "FORBIDDEN", "actorId is required");
    requireText(command.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    if (command.rules() == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "authority matrix rules are required");
    }
    return new ExceptionModels.CreateAuthorityMatrixDraftCommand(
      command.tenantId(),
      command.versionLabel().trim(),
      trimToNull(command.sourceVersionId()),
      command.rules(),
      command.actorId().trim(),
      command.idempotencyKey().trim(),
      command.correlationId().trim()
    );
  }

  private static List<ExceptionModels.AuthorityMatrixRuleDraft> normalizeAuthorityMatrixRules(
    List<ExceptionModels.AuthorityMatrixRuleDraft> rules
  ) {
    return rules.stream().filter(Objects::nonNull).map(rule -> new ExceptionModels.AuthorityMatrixRuleDraft(
      trimToNull(rule.ruleId()),
      trimToNull(rule.requestType()),
      new ExceptionModels.AuthorityMatrixCondition(rule.condition() == null || rule.condition().dimensions() == null
        ? Map.of()
        : normalizedMap(rule.condition().dimensions())),
      trimToNull(rule.amountUnit()),
      trimToNull(rule.amountMin()),
      trimToNull(rule.amountMax()),
      rule.approvalSteps() == null ? List.of() : rule.approvalSteps().stream().filter(Objects::nonNull)
        .map(step -> new ExceptionModels.AuthorityMatrixApprovalStep(
          trimToNull(step.stepId()),
          step.roleScopeRefs() == null ? List.of() : step.roleScopeRefs().stream()
            .filter(value -> !isBlank(value))
            .map(String::trim)
            .toList(),
          trimToNull(step.slaPolicyRef())
        ))
        .toList(),
      rule.priority(),
      trimToNull(rule.failClosedReason())
    )).toList();
  }

  private static List<ExceptionModels.AuthorityMatrixValidationMessage> validateAuthorityMatrixRules(
    List<ExceptionModels.AuthorityMatrixRuleDraft> rules
  ) {
    List<ExceptionModels.AuthorityMatrixValidationMessage> messages = new ArrayList<>();
    Set<String> deterministicKeys = new HashSet<>();
    boolean hasCatchAllFailClosedRule = false;
    if (rules.isEmpty()) {
      messages.add(new ExceptionModels.AuthorityMatrixValidationMessage(
        "POLICY_NOT_SATISFIED", "at least one configured authority matrix rule is required", null
      ));
    }
    for (ExceptionModels.AuthorityMatrixRuleDraft rule : rules) {
      String ruleId = rule.ruleId();
      if (isBlank(ruleId)) {
        messages.add(new ExceptionModels.AuthorityMatrixValidationMessage("VALIDATION_FAILED", "ruleId is required", null));
      }
      if (isBlank(rule.requestType())) {
        messages.add(new ExceptionModels.AuthorityMatrixValidationMessage("POLICY_NOT_SATISFIED", "configured requestType is required", ruleId));
      }
      if (isBlank(rule.amountUnit())) {
        messages.add(new ExceptionModels.AuthorityMatrixValidationMessage("POLICY_NOT_SATISFIED", "configured amountUnit is required", ruleId));
      }
      if (rule.approvalSteps().isEmpty()) {
        messages.add(new ExceptionModels.AuthorityMatrixValidationMessage("AUTHORITY_ROUTE_UNRESOLVED", "at least one configured approval step is required", ruleId));
      }
      for (ExceptionModels.AuthorityMatrixApprovalStep step : rule.approvalSteps()) {
        if (isBlank(step.stepId()) || step.roleScopeRefs().isEmpty()) {
          messages.add(new ExceptionModels.AuthorityMatrixValidationMessage(
            "AUTHORITY_ROUTE_UNRESOLVED", "approval steps require stepId and role/group refs", ruleId
          ));
        }
      }
      if (isBlank(rule.failClosedReason())) {
        messages.add(new ExceptionModels.AuthorityMatrixValidationMessage(
          "POLICY_NOT_SATISFIED", "failClosedReason is required for unresolved matrix outcomes", ruleId
        ));
      } else if (rule.condition().dimensions().isEmpty()) {
        hasCatchAllFailClosedRule = true;
      }
      String deterministicKey = authorityMatrixRuleKey(rule);
      if (!deterministicKeys.add(deterministicKey)) {
        messages.add(new ExceptionModels.AuthorityMatrixValidationMessage(
          "AMBIGUOUS_AUTHORITY_MATRIX_ROW", "duplicate configured conditions would make route resolution ambiguous", ruleId
        ));
      }
    }
    if (!hasCatchAllFailClosedRule) {
      messages.add(new ExceptionModels.AuthorityMatrixValidationMessage(
        "POLICY_NOT_SATISFIED", "a catch-all fail-closed rule is required", null
      ));
    }
    return List.copyOf(messages);
  }

  private static void validateApproveAuthorityMatrixCommand(ExceptionModels.ApproveAuthorityMatrixCommand command) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "authority matrix approval command is required");
    }
    requireTenant(command.tenantId());
    requireText(command.matrixVersionId(), "VALIDATION_FAILED", "matrixVersionId is required");
    requireText(command.actorId(), "FORBIDDEN", "actorId is required");
    requireText(command.approvalTicketRef(), "POLICY_NOT_SATISFIED", "approvalTicketRef is required");
    requireText(command.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    if (command.actorRoleRefs() == null || command.actorRoleRefs().isEmpty()) {
      throw new ExceptionServiceException("NOT_CURRENT_APPROVER", "actorRoleRefs are required");
    }
    if (command.conflictAttestation() == null || !command.conflictAttestation().noConflict()) {
      throw new ExceptionServiceException("SEPARATION_OF_DUTIES_VIOLATION", "approver must attest no conflict of interest");
    }
  }

  private static void validatePublishAuthorityMatrixCommand(ExceptionModels.PublishAuthorityMatrixCommand command) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "authority matrix publish command is required");
    }
    requireTenant(command.tenantId());
    requireText(command.matrixVersionId(), "VALIDATION_FAILED", "matrixVersionId is required");
    requireText(command.actorId(), "FORBIDDEN", "actorId is required");
    requireText(command.effectiveFrom(), "POLICY_NOT_SATISFIED", "effectiveFrom is required");
    requireText(command.idempotencyKey(), "VALIDATION_FAILED", "Idempotency-Key is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
  }

  private static void validateResolveAuthorityMatrixCommand(ExceptionModels.ResolveAuthorityMatrixCommand command) {
    if (command == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "authority matrix resolve command is required");
    }
    requireTenant(command.tenantId());
    requireText(command.requestType(), "POLICY_NOT_SATISFIED", "requestType is required");
    requireText(command.amountUnit(), "POLICY_NOT_SATISFIED", "amountUnit is required");
    requireText(command.amountValue(), "POLICY_NOT_SATISFIED", "amountValue is required");
    requireText(command.effectiveAt(), "POLICY_NOT_SATISFIED", "effectiveAt is required");
    requireText(command.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    if (command.dimensions() == null) {
      throw new ExceptionServiceException("POLICY_NOT_SATISFIED", "configured request dimensions are required");
    }
  }

  private static void requireVersion(int expectedVersion, int currentVersion) {
    if (expectedVersion != currentVersion) {
      throw new ExceptionServiceException("VERSION_CONFLICT", "expectedVersion does not match current authority matrix version");
    }
  }

  private static Instant parseInstant(String value, String fieldName) {
    try {
      return Instant.parse(value.trim());
    } catch (RuntimeException e) {
      throw new ExceptionServiceException("VALIDATION_FAILED", fieldName + " must be an ISO-8601 instant");
    }
  }

  private static boolean requestTypeMatches(String configured, String requested) {
    return configured.equals(requested) || "*".equals(configured);
  }

  private static boolean dimensionsMatch(Map<String, String> configured, Map<String, String> requestDimensions) {
    return configured.entrySet().stream().allMatch(entry -> Objects.equals(requestDimensions.get(entry.getKey()), entry.getValue()));
  }

  private static String authorityMatrixRuleKey(ExceptionModels.AuthorityMatrixRuleDraft rule) {
    return String.join("|",
      Objects.toString(rule.requestType(), ""),
      Objects.toString(rule.amountUnit(), ""),
      Objects.toString(rule.amountMin(), ""),
      Objects.toString(rule.amountMax(), ""),
      new TreeMap<>(rule.condition().dimensions()).toString()
    );
  }

  private static String authorityMatrixDraftHash(
    ExceptionModels.CreateAuthorityMatrixDraftCommand command,
    List<ExceptionModels.AuthorityMatrixRuleDraft> rules,
    String validationHash
  ) {
    return hash(String.join("|",
      command.tenantId().toString(), command.versionLabel(), Objects.toString(command.sourceVersionId(), ""),
      rules.toString(), validationHash, command.actorId()
    ));
  }

  private static ExceptionModels.AuthorityMatrixVersionResponse toAuthorityMatrixResponse(
    ExceptionModels.AuthorityMatrixVersionRecord record
  ) {
    return new ExceptionModels.AuthorityMatrixVersionResponse(
      record.tenantId(), record.matrixVersionId(), record.status(), record.versionLabel(), record.sourceVersionId(),
      record.rules(), record.validationMessages(), record.validationHash(), record.approvalTicketRef(), record.submittedBy(),
      record.approvedBy(), record.publishedBy(), record.effectiveFrom(), record.auditRef(), record.outboxEventType(),
      record.eventHash(), record.idempotencyKey(), record.correlationId(), record.version(), record.createdAt(), record.updatedAt()
    );
  }

  private static Map<String, String> normalizedMap(Map<String, String> values) {
    Map<String, String> normalized = new TreeMap<>();
    values.forEach((key, value) -> normalized.put(key.trim(), value == null ? "" : value.trim()));
    return normalized;
  }

  private static boolean containsRawPiiToken(Map<String, String> values) {
    return values.values().stream().filter(Objects::nonNull).anyMatch(value ->
      value.matches(".*\\b\\d{3}-\\d{2}-\\d{4}\\b.*")
        || value.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")
    );
  }

  private static String trimToNull(String value) {
    if (isBlank(value)) {
      return null;
    }
    return value.trim();
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

  private static String eligibilityExceptionRequestHash(
    ExceptionModels.CreateEligibilityExceptionRequest request,
    ExceptionModels.EligibilityExceptionPolicy policy,
    String narrativeRedacted,
    String approvalRouteHash
  ) {
    ExceptionModels.EligibilityFindingRef findingRef = request.findingRef();
    return hash(String.join("|",
      request.tenantId().toString(),
      request.quoteId(),
      request.scenarioId(),
      Objects.toString(request.lockId(), ""),
      findingRef.eligibilityResultId(),
      findingRef.findingId(),
      findingRef.ruleCode(),
      findingRef.ruleVersionId(),
      findingRef.severity(),
      findingRef.originalResultHash(),
      request.exceptionScope().scopeType(),
      request.exceptionScope().attributes().toString(),
      request.reasonCode(),
      narrativeRedacted,
      Objects.toString(request.compensatingFactors(), ""),
      Objects.toString(request.evidenceRefs(), ""),
      Objects.toString(request.desiredExpiration(), ""),
      Objects.toString(request.relatedConcessionRequestId(), ""),
      policy.policyVersionId(),
      policy.authorityMatrixVersionId(),
      approvalRouteHash
    ));
  }

  private static String eligibilityExceptionEventHash(
    ExceptionModels.CreateEligibilityExceptionRequest request,
    ExceptionModels.EligibilityExceptionPolicy policy,
    String requestHash,
    String approvalRouteHash
  ) {
    return hash(String.join("|",
      request.tenantId().toString(),
      request.quoteId(),
      request.findingRef().findingId(),
      request.findingRef().ruleCode(),
      request.findingRef().ruleVersionId(),
      request.findingRef().originalResultHash(),
      request.exceptionScope().scopeType(),
      request.reasonCode(),
      ExceptionModels.EligibilityExceptionRequestStatus.SUBMITTED.name(),
      policy.policyVersionId(),
      policy.authorityMatrixVersionId(),
      approvalRouteHash,
      requestHash
    ));
  }

  private static ExceptionModels.ExceptionHistorySearchRequest normalizeHistorySearch(
    ExceptionModels.ExceptionHistorySearchRequest request,
    String requiredPermission
  ) {
    if (request == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "exception history search request is required");
    }
    requireTenant(request.tenantId());
    if (request.subjectType() == null) {
      throw new ExceptionServiceException("VALIDATION_FAILED", "subjectType is required");
    }
    requireText(request.subjectId(), "VALIDATION_FAILED", "subjectId is required");
    requireText(request.actorId(), "FORBIDDEN", "actorId is required");
    requireText(request.correlationId(), "VALIDATION_FAILED", "correlationId is required");
    Set<String> permissions = request.permissions() == null ? Set.of() : new java.util.TreeSet<>(request.permissions());
    if (!permissions.contains(requiredPermission)) {
      throw new ExceptionServiceException("FORBIDDEN", "missing permission: " + requiredPermission);
    }
    return new ExceptionModels.ExceptionHistorySearchRequest(
      request.tenantId(),
      request.subjectType(),
      request.subjectId().trim(),
      request.actorId().trim(),
      permissions,
      request.includeRawJson(),
      request.correlationId().trim()
    );
  }

  private static boolean matchesConcession(
    ExceptionModels.PricingConcessionRequestRecord record,
    ExceptionModels.ExceptionHistorySearchRequest search
  ) {
    return record.tenantId().equals(search.tenantId()) && switch (search.subjectType()) {
      case QUOTE -> record.quoteId().equals(search.subjectId());
      case LOCK -> Objects.equals(record.lockId(), search.subjectId());
      case CONCESSION_REQUEST -> record.concessionRequestId().equals(search.subjectId());
      case ACTOR -> record.actorId().equals(search.subjectId());
      case CORRELATION_ID -> record.correlationId().equals(search.subjectId());
      case ELIGIBILITY_EXCEPTION -> false;
    };
  }

  private static boolean matchesLegacyException(
    ExceptionModels.ExceptionRequestRecord record,
    ExceptionModels.ExceptionHistorySearchRequest search
  ) {
    return search.subjectType() == ExceptionModels.ExceptionHistorySubjectType.QUOTE
      && record.placeholderQuoteReference().equals(search.subjectId());
  }

  private static boolean matchesApplication(
    ExceptionModels.ConcessionApplicationRecord record,
    ExceptionModels.ExceptionHistorySearchRequest search
  ) {
    return record.tenantId().equals(search.tenantId()) && switch (search.subjectType()) {
      case QUOTE -> record.quoteId().equals(search.subjectId());
      case LOCK -> Objects.equals(record.lockId(), search.subjectId());
      case CONCESSION_REQUEST -> record.concessionRequestId().equals(search.subjectId());
      case ACTOR -> record.appliedBy().equals(search.subjectId());
      case CORRELATION_ID -> record.correlationId().equals(search.subjectId());
      case ELIGIBILITY_EXCEPTION -> false;
    };
  }

  private static boolean matchesEligibility(
    ExceptionModels.EligibilityExceptionRequestRecord record,
    ExceptionModels.ExceptionHistorySearchRequest search
  ) {
    return record.tenantId().equals(search.tenantId()) && switch (search.subjectType()) {
      case QUOTE -> record.quoteId().equals(search.subjectId());
      case LOCK -> Objects.equals(record.lockId(), search.subjectId());
      case ELIGIBILITY_EXCEPTION -> record.exceptionRequestId().equals(search.subjectId());
      case CONCESSION_REQUEST -> Objects.equals(record.relatedConcessionRequestId(), search.subjectId());
      case ACTOR -> record.actorId().equals(search.subjectId());
      case CORRELATION_ID -> record.correlationId().equals(search.subjectId());
    };
  }

  private static boolean matchesMonitoringSignal(
    ExceptionModels.MonitoringSignalRecord record,
    ExceptionModels.ExceptionHistorySearchRequest search
  ) {
    return record.tenantId().equals(search.tenantId()) && switch (search.subjectType()) {
      case CONCESSION_REQUEST -> record.concessionRequestId().equals(search.subjectId());
      case ACTOR -> record.actorId().equals(search.subjectId());
      case CORRELATION_ID -> record.correlationId().equals(search.subjectId());
      case QUOTE, LOCK, ELIGIBILITY_EXCEPTION -> false;
    };
  }

  private static boolean matchesManualPriceEdit(
    ExceptionModels.ManualPriceEditAttemptRecord record,
    ExceptionModels.ExceptionHistorySearchRequest search
  ) {
    return record.tenantId().equals(search.tenantId()) && switch (search.subjectType()) {
      case QUOTE -> record.quoteId().equals(search.subjectId());
      case LOCK -> Objects.equals(record.lockId(), search.subjectId());
      case ACTOR -> record.actorId().equals(search.subjectId());
      case CORRELATION_ID -> record.correlationId().equals(search.subjectId());
      case CONCESSION_REQUEST, ELIGIBILITY_EXCEPTION -> false;
    };
  }

  private static boolean matchesActorOrCorrelation(
    String actorId,
    String correlationId,
    ExceptionModels.ExceptionHistorySearchRequest search
  ) {
    return (search.subjectType() == ExceptionModels.ExceptionHistorySubjectType.ACTOR && Objects.equals(actorId, search.subjectId()))
      || (search.subjectType() == ExceptionModels.ExceptionHistorySubjectType.CORRELATION_ID && Objects.equals(correlationId, search.subjectId()));
  }

  private static ExceptionModels.TimelineEvent legacyExceptionTimelineEvent(
    ExceptionModels.ExceptionRequestRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    ExceptionModels.ExceptionHistoryAction action = record.state() == ExceptionModels.ExceptionState.REJECTED
      ? ExceptionModels.ExceptionHistoryAction.REJECTED
      : ExceptionModels.ExceptionHistoryAction.REQUESTED;
    String eventHash = hash(record.exceptionRequestId() + "|" + record.placeholderQuoteReference() + "|" + record.state());
    return timelineEvent(record.updatedAt(), record.exceptionRequestId(), record.exceptionRequestId(), 1, "system", action,
      "DRAFT->" + record.state(), null, null, record.placeholderQuoteReference(), null, Map.of(),
      "ExceptionRequest" + record.state() + ".v1", eventHash, rawJsonAllowed, evidenceAllowed, Map.of());
  }

  private static ExceptionModels.TimelineEvent concessionTimelineEvent(
    ExceptionModels.PricingConcessionRequestRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    return timelineEvent(record.createdAt(), record.concessionRequestId(), record.concessionRequestId(), record.version(), record.actorId(),
      ExceptionModels.ExceptionHistoryAction.REQUESTED, "NONE->" + record.status(), record.reasonCode(), record.requestedAmount(),
      record.quoteId(), record.lockId(), configVersions(Map.of(
        "concessionPolicyVersionId", record.concessionPolicyVersionId(),
        "authorityMatrixVersionId", record.authorityMatrixVersionId(),
        "reasonCodeVersionId", record.reasonCodeVersionId()
      )), record.outboxEventType(), record.requestHash(), rawJsonAllowed, evidenceAllowed, evidenceRefs(record.evidenceRefs()));
  }

  private static ExceptionModels.TimelineEvent approvalTimelineEvent(
    ExceptionModels.ApprovalDecisionRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    return timelineEvent(record.createdAt(), record.decisionId(), record.concessionRequestId(), record.aggregateVersion(), record.actorId(),
      ExceptionModels.ExceptionHistoryAction.APPROVED, "SUBMITTED->APPROVED_PENDING_APPLICATION", record.decisionReasonCode(), null,
      null, null, configVersions(Map.of("authorityMatrixVersionId", record.authorityMatrixVersionId())), record.outboxEventType(),
      record.eventHash(), rawJsonAllowed, evidenceAllowed, Map.of("auditRef", record.auditRef()));
  }

  private static ExceptionModels.TimelineEvent applicationTimelineEvent(
    ExceptionModels.ConcessionApplicationRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    return timelineEvent(record.appliedAt(), record.applicationId(), record.concessionRequestId(), record.version(), record.appliedBy(),
      ExceptionModels.ExceptionHistoryAction.APPLIED, "APPROVED_PENDING_APPLICATION->APPLIED", null, record.appliedAmount(),
      record.quoteId(), record.lockId(), configVersions(Map.of(
        "pricingRuleVersionId", record.pricingRuleVersionId(),
        "policyVersionId", record.policyVersionId(),
        "precedenceConfigVersionId", record.precedenceConfigVersionId()
      )), record.outboxEventType(), record.replayHash(), rawJsonAllowed, evidenceAllowed, Map.of("pricingLedgerEntryId", record.pricingLedgerEntryId()));
  }

  private static ExceptionModels.TimelineEvent eligibilityTimelineEvent(
    ExceptionModels.EligibilityExceptionRequestRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    return timelineEvent(record.createdAt(), record.exceptionRequestId(), record.exceptionRequestId(), record.version(), record.actorId(),
      ExceptionModels.ExceptionHistoryAction.REQUESTED, "NONE->" + record.status(), record.reasonCode(), null, record.quoteId(), record.lockId(),
      configVersions(Map.of("policyVersionId", record.policyVersionId(), "authorityMatrixVersionId", record.authorityMatrixVersionId(),
        "ruleVersionId", record.findingRef().ruleVersionId())), record.outboxEventType(), record.eventHash(), rawJsonAllowed, evidenceAllowed,
      eligibilityEvidenceRefs(record.evidenceRefs()));
  }

  private static ExceptionModels.TimelineEvent monitoringSignalTimelineEvent(
    ExceptionModels.MonitoringSignalRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    return timelineEvent(record.observedAt(), record.signalId(), record.concessionRequestId(), 1, record.actorId(),
      ExceptionModels.ExceptionHistoryAction.MONITORING_ALERT, "MONITORING_SIGNAL_RECORDED", record.signalType().name(), null,
      null, null, Map.of("detectorVersionId", record.detectorVersionId()), "ConcessionMonitoringSignalRecorded.v1", record.signalHash(),
      rawJsonAllowed, evidenceAllowed, Map.of("sourceEventId", record.sourceEventId()));
  }

  private static ExceptionModels.TimelineEvent monitoringAlertTimelineEvent(
    ExceptionModels.MonitoringAlertRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    return timelineEvent(record.openedAt(), record.alertId(), record.signalId(), record.version(), "system", ExceptionModels.ExceptionHistoryAction.MONITORING_ALERT,
      "NONE->" + record.status(), record.severity().name(), null, null, null, Map.of("detectorVersionId", record.detectorVersionId()),
      record.outboxEventType(), record.replayHash(), rawJsonAllowed, evidenceAllowed, record.evidenceSnapshot());
  }

  private static ExceptionModels.TimelineEvent alertDispositionTimelineEvent(
    ExceptionModels.AlertDispositionRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    return timelineEvent(record.createdAt(), record.dispositionId(), record.alertId(), record.alertVersion(), record.actorId(),
      ExceptionModels.ExceptionHistoryAction.ALERT_DISPOSITIONED, record.previousStatus() + "->" + record.newStatus(), record.reasonCode(), null,
      null, null, Map.of(), record.outboxEventType(), record.dispositionHash(), rawJsonAllowed, evidenceAllowed, Map.of("auditRef", record.auditRef()));
  }

  private static ExceptionModels.TimelineEvent manualPriceEditTimelineEvent(
    ExceptionModels.ManualPriceEditAttemptRecord record,
    boolean rawJsonAllowed,
    boolean evidenceAllowed
  ) {
    return timelineEvent(record.createdAt(), record.attemptId(), record.attemptId(), 1, record.actorId(),
      ExceptionModels.ExceptionHistoryAction.PRICE_MUTATION_BLOCKED, "NONE->BLOCKED", record.denialReason(), null, record.quoteId(), record.lockId(),
      Map.of("policyVersionId", record.policyVersionId()), record.outboxEventType(), record.eventHash(), rawJsonAllowed, evidenceAllowed,
      Map.of("sourceSurface", record.sourceSurface(), "guardedFields", record.fieldNames().toString()));
  }

  private static ExceptionModels.TimelineEvent timelineEvent(
    Instant occurredAt,
    String eventId,
    String aggregateRef,
    int aggregateVersion,
    String actorId,
    ExceptionModels.ExceptionHistoryAction action,
    String statusTransition,
    String reason,
    ExceptionModels.ConcessionAmount amount,
    String targetQuoteId,
    String targetLockId,
    Map<String, String> configVersions,
    String eventType,
    String eventHash,
    boolean rawJsonAllowed,
    boolean evidenceAllowed,
    Map<String, String> evidenceRefs
  ) {
    Map<String, String> safeEvidenceRefs = evidenceAllowed ? new TreeMap<>(evidenceRefs) : Map.of("redaction", "evidence_refs_redacted");
    return new ExceptionModels.TimelineEvent(
      occurredAt,
      eventId,
      aggregateRef,
      aggregateVersion,
      actorId,
      action,
      statusTransition,
      reason,
      amount,
      targetQuoteId,
      targetLockId,
      new TreeMap<>(configVersions),
      eventType,
      eventHash,
      !isBlank(eventHash),
      rawJsonAllowed,
      !rawJsonAllowed || !evidenceAllowed,
      safeEvidenceRefs
    );
  }

  private static ExceptionModels.VersionGraph buildVersionGraph(List<ExceptionModels.TimelineEvent> events) {
    Map<String, String> configVersions = new TreeMap<>();
    List<String> eventIds = events.stream().map(ExceptionModels.TimelineEvent::eventId).toList();
    for (ExceptionModels.TimelineEvent event : events) {
      event.configVersions().forEach((key, value) -> {
        if (!isBlank(value)) {
          configVersions.put(key + ":" + value, value);
        }
      });
    }
    return new ExceptionModels.VersionGraph(eventIds, configVersions, hash(eventIds.toString() + "|" + configVersions));
  }

  private static Map<String, String> configVersions(Map<String, String> versions) {
    Map<String, String> normalized = new TreeMap<>();
    versions.forEach((key, value) -> {
      if (!isBlank(value)) {
        normalized.put(key, value);
      }
    });
    return normalized;
  }

  private static Map<String, String> evidenceRefs(List<ExceptionModels.ConcessionEvidenceRef> refs) {
    Map<String, String> evidence = new LinkedHashMap<>();
    if (refs != null) {
      for (int i = 0; i < refs.size(); i++) {
        ExceptionModels.ConcessionEvidenceRef ref = refs.get(i);
        evidence.put("evidenceRef" + i, ref.evidenceType() + ":" + ref.checksum());
      }
    }
    return evidence;
  }

  private static Map<String, String> eligibilityEvidenceRefs(List<ExceptionModels.EligibilityExceptionEvidenceRef> refs) {
    Map<String, String> evidence = new LinkedHashMap<>();
    if (refs != null) {
      for (int i = 0; i < refs.size(); i++) {
        ExceptionModels.EligibilityExceptionEvidenceRef ref = refs.get(i);
        evidence.put("evidenceRef" + i, ref.evidenceType() + ":" + ref.checksum());
      }
    }
    return evidence;
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
