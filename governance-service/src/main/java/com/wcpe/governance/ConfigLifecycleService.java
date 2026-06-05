package com.wcpe.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConfigLifecycleService {
  public static final String SUBMIT_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-artifacts/{artifactId}/versions/{versionId}/submit";
  public static final String APPROVAL_DECISION_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-approval-requests/{approvalRequestId}/decisions";
  public static final String PUBLISH_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-artifacts/{artifactId}/versions/{versionId}/publish";
  public static final String SCHEDULE_PUBLISH_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-artifacts/{artifactId}/versions/{versionId}/schedule-publish";
  public static final String SUSPEND_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-artifacts/{artifactId}/versions/{versionId}/suspend";
  public static final String ROLLBACK_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-artifacts/{artifactId}/versions/{versionId}/rollback";
  public static final String SUBMIT_PERMISSION = "admin.config.submit";
  public static final String APPROVE_PERMISSION = "admin.config.approve";
  public static final String PUBLISH_PERMISSION = "admin.config.publish";
  public static final String EMERGENCY_PERMISSION = "admin.config.emergency";
  public static final String ROLLBACK_PERMISSION = "admin.config.rollback";
  public static final String AUDIT_ACTION = "CONFIG_LIFECYCLE_TRANSITION_RECORDED";

  private final Clock clock;
  private final Map<String, ConfigLifecycleVersion> versionsByTenantAndVersion = new HashMap<>();
  private final Map<String, ApprovalRequest> approvalRequestsByTenantAndVersion = new HashMap<>();
  private final Map<String, ConfigLifecycleResult> idempotencyEntries = new HashMap<>();
  private final List<ApprovalDecision> approvalDecisions = new ArrayList<>();
  private final List<PublishSchedule> publishSchedules = new ArrayList<>();
  private final List<LifecycleTransition> transitions = new ArrayList<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public ConfigLifecycleService() {
    this(Clock.systemUTC());
  }

  public ConfigLifecycleService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<ConfigLifecycleResult> handle(ConfigLifecycleCommand command) {
    GovernanceValidationResult<ConfigLifecycleCommand> validation = validateCommand(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalCommand(command));
    String idempotencyLookupKey = command.tenantId() + ":" + command.idempotencyKey();
    ConfigLifecycleResult existing = idempotencyEntries.get(idempotencyLookupKey);
    if (existing != null) {
      if (!existing.replayRef().equals(requestHash)) {
        return GovernanceValidationResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return GovernanceValidationResult.success(existing);
    }

    ConfigLifecycleVersion current = currentVersion(command);
    if (current.status() != command.expectedStatus()) {
      return GovernanceValidationResult.failure("INVALID_TRANSITION: expected status does not match current status");
    }
    if (!current.etag().equals(command.expectedEtag())) {
      return GovernanceValidationResult.failure("VERSION_CONFLICT");
    }

    GovernanceValidationResult<ConfigLifecycleResult> transition = switch (command.action()) {
      case VALIDATE -> validate(command, current, requestHash);
      case SUBMIT -> submit(command, current, requestHash);
      case APPROVE -> approve(command, current, requestHash);
      case REJECT, REQUEST_CHANGES -> decisionWithoutApproval(command, current, requestHash);
      case PUBLISH -> publish(command, current, requestHash, false);
      case SCHEDULE_PUBLISH -> publish(command, current, requestHash, true);
      case SUSPEND -> simpleTransition(command, current, ConfigLifecycleState.SUSPENDED, requestHash);
      case SUPERSEDE -> simpleTransition(command, current, ConfigLifecycleState.SUPERSEDED, requestHash);
      case ROLLBACK -> simpleTransition(command, current, ConfigLifecycleState.ROLLBACK_DRAFT, requestHash);
      case EXPIRE -> simpleTransition(command, current, ConfigLifecycleState.EXPIRED, requestHash);
      case ARCHIVE -> simpleTransition(command, current, ConfigLifecycleState.ARCHIVED, requestHash);
    };
    if (transition.valid()) {
      idempotencyEntries.put(idempotencyLookupKey, transition.value().orElseThrow());
    }
    return transition;
  }

  public List<ConfigLifecycleVersion> versionsForTenant(String tenantId) {
    return versionsByTenantAndVersion.values().stream()
        .filter(version -> version.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(ConfigLifecycleVersion::artifactId).thenComparing(ConfigLifecycleVersion::versionId))
        .toList();
  }

  public List<ApprovalRequest> approvalRequests() {
    return List.copyOf(approvalRequestsByTenantAndVersion.values());
  }

  public List<ApprovalDecision> approvalDecisions() {
    return List.copyOf(approvalDecisions);
  }

  public List<PublishSchedule> publishSchedules() {
    return List.copyOf(publishSchedules);
  }

  public List<LifecycleTransition> transitions() {
    return List.copyOf(transitions);
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private GovernanceValidationResult<ConfigLifecycleResult> validate(ConfigLifecycleCommand command, ConfigLifecycleVersion current, String requestHash) {
    if (isBlank(command.validationRunId()) || isBlank(command.validationResultHash()) || command.validationCompletedAt() == null) {
      return GovernanceValidationResult.failure("VALIDATION_STALE");
    }
    if (command.validationBlocking()) {
      return GovernanceValidationResult.failure("VALIDATION_BLOCKING");
    }
    ConfigLifecycleVersion next = current.withValidation(command.validationRunId(), command.validationResultHash(), command.validationCompletedAt(), false, nextEtag(current));
    return record(command, current, next, null, null, null, List.of(), requestHash);
  }

  private GovernanceValidationResult<ConfigLifecycleResult> submit(ConfigLifecycleCommand command, ConfigLifecycleVersion current, String requestHash) {
    GovernanceValidationResult<Void> validation = requireFreshNonBlockingValidation(command, current);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    if (command.policy().requiredApprovalGroups().isEmpty() && !command.policy().allowAutoPublish()) {
      return GovernanceValidationResult.failure("APPROVAL_REQUIRED: approval policy is required");
    }
    ConfigLifecycleVersion next = current.withStatus(ConfigLifecycleState.SUBMITTED, current.revision() + 1, nextEtag(current), current.effectiveStart(), current.effectiveEnd());
    ApprovalRequest approvalRequest =
        new ApprovalRequest(
            deterministicId(command.tenantId(), command.versionId(), "approval-request"),
            command.tenantId(),
            command.artifactId(),
            command.versionId(),
            "OPEN",
            command.policy().requiredApprovalGroups(),
            command.actorId(),
            clock.instant());
    return record(command, current, next, approvalRequest, null, null, List.of(), requestHash);
  }

  private GovernanceValidationResult<ConfigLifecycleResult> approve(ConfigLifecycleCommand command, ConfigLifecycleVersion current, String requestHash) {
    ApprovalRequest request = approvalRequestsByTenantAndVersion.get(key(command.tenantId(), command.versionId()));
    if (request == null) {
      return GovernanceValidationResult.failure("APPROVAL_REQUIRED");
    }
    if (!command.policy().allowEmergencySelfApproval() && command.actorId().equals(current.lastMaterialEditorId())) {
      return GovernanceValidationResult.failure("SOD_VIOLATION");
    }
    if (command.policy().allowEmergencySelfApproval()
        && command.policy().requireSecondApprovalForEmergency()
        && command.actorId().equals(current.lastMaterialEditorId())
        && approvalDecisions.stream().noneMatch(decision -> decision.approvalRequestId().equals(request.approvalRequestId()) && !decision.approverId().equals(command.actorId()))) {
      return GovernanceValidationResult.failure("SOD_VIOLATION: second approval is required for emergency override");
    }
    if (!command.policy().requiredApprovalGroups().isEmpty()
        && (isBlank(command.actorGroup()) || !command.policy().requiredApprovalGroups().containsKey(command.actorGroup()))) {
      return GovernanceValidationResult.failure("PUBLISH_POLICY_DENIED: approver group is not allowed by tenant policy");
    }
    ApprovalDecision decision = decision(command, request, "APPROVE");
    ConfigLifecycleVersion next = current.withStatus(ConfigLifecycleState.APPROVED, current.revision() + 1, nextEtag(current), current.effectiveStart(), current.effectiveEnd());
    return record(command, current, next, null, decision, null, List.of(), requestHash);
  }

  private GovernanceValidationResult<ConfigLifecycleResult> decisionWithoutApproval(
      ConfigLifecycleCommand command, ConfigLifecycleVersion current, String requestHash) {
    ApprovalRequest request = approvalRequestsByTenantAndVersion.get(key(command.tenantId(), command.versionId()));
    if (request == null) {
      return GovernanceValidationResult.failure("APPROVAL_REQUIRED");
    }
    String decisionName = command.action() == ConfigLifecycleAction.REJECT ? "REJECT" : "REQUEST_CHANGES";
    ConfigLifecycleState nextStatus = command.action() == ConfigLifecycleAction.REJECT ? ConfigLifecycleState.REJECTED : ConfigLifecycleState.DRAFT;
    ConfigLifecycleVersion next = current.withStatus(nextStatus, current.revision() + 1, nextEtag(current), current.effectiveStart(), current.effectiveEnd());
    return record(command, current, next, null, decision(command, request, decisionName), null, List.of(), requestHash);
  }

  private GovernanceValidationResult<ConfigLifecycleResult> publish(
      ConfigLifecycleCommand command, ConfigLifecycleVersion current, String requestHash, boolean scheduled) {
    GovernanceValidationResult<Void> validation = requireFreshNonBlockingValidation(command, current);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    if (current.status() != ConfigLifecycleState.APPROVED) {
      return GovernanceValidationResult.failure("APPROVAL_REQUIRED");
    }
    if (command.requestedEffectiveStart() == null) {
      return GovernanceValidationResult.failure("PUBLISH_POLICY_DENIED: effective start is required");
    }
    if (command.requestedEffectiveEnd() != null && !command.requestedEffectiveEnd().isAfter(command.requestedEffectiveStart())) {
      return GovernanceValidationResult.failure("PUBLISH_POLICY_DENIED: effective end must be after effective start");
    }
    List<ConfigLifecycleVersion> activeConflicts = activePublishedConflicts(command, current);
    if (!activeConflicts.isEmpty() && !command.policy().allowActivePrecedenceOverlap()) {
      return GovernanceValidationResult.failure("PUBLISH_WINDOW_OVERLAP");
    }
    List<ConfigLifecycleVersion> affected =
        activeConflicts.stream()
            .map(version -> version.withStatus(ConfigLifecycleState.SUPERSEDED, version.revision() + 1, nextEtag(version), version.effectiveStart(), command.requestedEffectiveStart()))
            .toList();
    ConfigLifecycleVersion next =
        current.withStatus(
            scheduled ? ConfigLifecycleState.SCHEDULED : ConfigLifecycleState.PUBLISHED,
            current.revision() + 1,
            nextEtag(current),
            command.requestedEffectiveStart(),
            command.requestedEffectiveEnd());
    PublishSchedule schedule =
        new PublishSchedule(
            deterministicId(command.tenantId(), command.versionId(), command.action().name(), command.requestedEffectiveStart().toString()),
            command.tenantId(),
            command.artifactId(),
            command.versionId(),
            command.requestedEffectiveStart(),
            command.requestedEffectiveEnd(),
            scheduled ? "SCHEDULED" : "EXECUTED",
            command.actorId(),
            clock.instant(),
            scheduled ? null : clock.instant());
    return record(command, current, next, null, null, schedule, affected, requestHash);
  }

  private GovernanceValidationResult<ConfigLifecycleResult> simpleTransition(
      ConfigLifecycleCommand command, ConfigLifecycleVersion current, ConfigLifecycleState nextStatus, String requestHash) {
    ConfigLifecycleVersion next = current.withStatus(nextStatus, current.revision() + 1, nextEtag(current), current.effectiveStart(), current.effectiveEnd());
    return record(command, current, next, null, null, null, List.of(), requestHash);
  }

  private GovernanceValidationResult<ConfigLifecycleResult> record(
      ConfigLifecycleCommand command,
      ConfigLifecycleVersion before,
      ConfigLifecycleVersion after,
      ApprovalRequest approvalRequest,
      ApprovalDecision approvalDecision,
      PublishSchedule publishSchedule,
      List<ConfigLifecycleVersion> affectedPublishedVersions,
      String requestHash) {
    Instant now = clock.instant();
    LifecycleTransition transition =
        new LifecycleTransition(
            deterministicId(command.tenantId(), command.versionId(), command.action().name(), Integer.toString(after.revision())),
            command.tenantId(),
            command.artifactId(),
            command.versionId(),
            before.status(),
            after.status(),
            command.action(),
            command.reasonCode(),
            command.actorId(),
            command.correlationId(),
            now,
            command.policy().policyVersionId());
    String eventType = eventType(command.action());
    String eventId = deterministicId(transition.transitionId(), command.correlationId(), "event");
    String auditId = deterministicId(transition.transitionId(), command.actorId(), "audit");
    ConfigApiOutboxEvent event =
        new ConfigApiOutboxEvent(
            eventId,
            eventType,
            1,
            command.tenantId(),
            command.artifactId(),
            command.versionId(),
            command.actorId(),
            command.correlationId(),
            transition.transitionId(),
            command.idempotencyKey(),
            now,
            Map.of(
                "versionId", after.versionId(),
                "fromStatus", before.status().name(),
                "status", after.status().name(),
                "payloadHash", after.payloadHash(),
                "policyVersionId", command.policy().policyVersionId(),
                "cacheInvalidation", cacheInvalidationMarker(command.action())));
    ConfigApiAuditRecord audit =
        new ConfigApiAuditRecord(
            auditId,
            command.tenantId(),
            command.artifactId(),
            command.versionId(),
            command.actorId(),
            AUDIT_ACTION,
            before.status().name() + ":" + before.payloadHash(),
            after.status().name() + ":" + after.payloadHash(),
            command.correlationId(),
            now);

    versionsByTenantAndVersion.put(key(after.tenantId(), after.versionId()), after);
    for (ConfigLifecycleVersion affected : affectedPublishedVersions) {
      versionsByTenantAndVersion.put(key(affected.tenantId(), affected.versionId()), affected);
    }
    if (approvalRequest != null) {
      approvalRequestsByTenantAndVersion.put(key(approvalRequest.tenantId(), approvalRequest.versionId()), approvalRequest);
    }
    if (approvalDecision != null) {
      approvalDecisions.add(approvalDecision);
    }
    if (publishSchedule != null) {
      publishSchedules.add(publishSchedule);
    }
    transitions.add(transition);
    outboxEvents.add(event);
    auditRecords.add(audit);
    return GovernanceValidationResult.success(
        new ConfigLifecycleResult(after, approvalRequest, approvalDecision, publishSchedule, transition, audit, event, affectedPublishedVersions, auditId, requestHash));
  }

  private GovernanceValidationResult<Void> requireFreshNonBlockingValidation(ConfigLifecycleCommand command, ConfigLifecycleVersion current) {
    if (current.validationBlocking() || isBlank(current.validationRunId()) || current.validationCompletedAt() == null) {
      return GovernanceValidationResult.failure("VALIDATION_STALE");
    }
    if (command.policy().validationFreshness() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: validation freshness policy is required");
    }
    if (current.validationCompletedAt().plus(command.policy().validationFreshness()).isBefore(clock.instant())) {
      return GovernanceValidationResult.failure("VALIDATION_STALE");
    }
    return GovernanceValidationResult.success(null);
  }

  private GovernanceValidationResult<ConfigLifecycleCommand> validateCommand(ConfigLifecycleCommand command) {
    if (command == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: command is required");
    }
    if (!isUuid(command.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(command.idempotencyKey())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: idempotency key is required");
    }
    if (isBlank(command.actorId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: actorId is required");
    }
    if (isBlank(command.artifactId()) || isBlank(command.versionId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: artifact and version are required");
    }
    if (command.action() == null || command.expectedStatus() == null || isBlank(command.expectedEtag())) {
      return GovernanceValidationResult.failure("INVALID_TRANSITION: action, expected status, and etag are required");
    }
    if (command.policy() == null || isBlank(command.policy().policyVersionId())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: lifecycle policy is required");
    }
    if (command.version() == null && !versionsByTenantAndVersion.containsKey(key(command.tenantId(), command.versionId()))) {
      return GovernanceValidationResult.failure("NOT_FOUND");
    }
    if (isBlank(command.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: correlationId is required");
    }
    return GovernanceValidationResult.success(command);
  }

  private ConfigLifecycleVersion currentVersion(ConfigLifecycleCommand command) {
    ConfigLifecycleVersion current = versionsByTenantAndVersion.get(key(command.tenantId(), command.versionId()));
    return current == null ? command.version() : current;
  }

  private List<ConfigLifecycleVersion> activePublishedConflicts(ConfigLifecycleCommand command, ConfigLifecycleVersion current) {
    return versionsByTenantAndVersion.values().stream()
        .filter(version -> version.tenantId().equals(command.tenantId()))
        .filter(version -> version.artifactId().equals(command.artifactId()))
        .filter(version -> version.contextKey().equals(current.contextKey()))
        .filter(version -> version.status() == ConfigLifecycleState.PUBLISHED)
        .filter(version -> !version.versionId().equals(command.versionId()))
        .filter(version -> windowsOverlap(version.effectiveStart(), version.effectiveEnd(), command.requestedEffectiveStart(), command.requestedEffectiveEnd()))
        .toList();
  }

  private boolean windowsOverlap(Instant leftStart, Instant leftEnd, Instant rightStart, Instant rightEnd) {
    Instant leftClosedEnd = leftEnd == null ? Instant.MAX : leftEnd;
    Instant rightClosedEnd = rightEnd == null ? Instant.MAX : rightEnd;
    return leftStart.isBefore(rightClosedEnd) && rightStart.isBefore(leftClosedEnd);
  }

  private ApprovalDecision decision(ConfigLifecycleCommand command, ApprovalRequest request, String decisionName) {
    return new ApprovalDecision(
        deterministicId(request.approvalRequestId(), command.actorId(), decisionName, Integer.toString(approvalDecisions.size() + 1)),
        request.approvalRequestId(),
        decisionName,
        command.actorId(),
        command.actorGroup(),
        command.reasonCode(),
        command.comments(),
        clock.instant());
  }

  private String eventType(ConfigLifecycleAction action) {
    return switch (action) {
      case SUBMIT -> "ConfigSubmittedForApproval.v1";
      case APPROVE, REJECT, REQUEST_CHANGES -> "ConfigApprovalDecisionRecorded.v1";
      case PUBLISH, SCHEDULE_PUBLISH -> "ConfigVersionPublished.v1";
      case SUSPEND -> "ConfigVersionSuspended.v1";
      case ROLLBACK -> "ConfigRollbackInitiated.v1";
      default -> "ConfigLifecycleTransitionRecorded.v1";
    };
  }

  private String cacheInvalidationMarker(ConfigLifecycleAction action) {
    return switch (action) {
      case PUBLISH, SCHEDULE_PUBLISH, SUSPEND, ROLLBACK, EXPIRE -> "required";
      default -> "not-required";
    };
  }

  private String nextEtag(ConfigLifecycleVersion version) {
    return "v" + (version.revision() + 1) + "-" + version.payloadHash().substring(0, Math.min(16, version.payloadHash().length()));
  }

  private String canonicalCommand(ConfigLifecycleCommand command) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.actorGroup() == null ? "" : command.actorGroup(),
        command.artifactId(),
        command.versionId(),
        command.action().name(),
        command.expectedStatus().name(),
        command.expectedEtag(),
        command.policy().policyVersionId(),
        command.validationRunId() == null ? "" : command.validationRunId(),
        command.validationResultHash() == null ? "" : command.validationResultHash(),
        command.validationCompletedAt() == null ? "" : command.validationCompletedAt().toString(),
        Boolean.toString(command.validationBlocking()),
        command.requestedEffectiveStart() == null ? "" : command.requestedEffectiveStart().toString(),
        command.requestedEffectiveEnd() == null ? "" : command.requestedEffectiveEnd().toString(),
        command.reasonCode() == null ? "" : command.reasonCode(),
        command.comments() == null ? "" : command.comments(),
        command.correlationId(),
        canonicalMap(command.evidenceRefs()));
  }

  private String canonicalMap(Map<String, String> map) {
    return map.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String deterministicId(String... parts) {
    return UUID.nameUUIDFromBytes(String.join(":", parts).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required", ex);
    }
  }

  private String key(String tenantId, String versionId) {
    return tenantId + ":" + versionId;
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
