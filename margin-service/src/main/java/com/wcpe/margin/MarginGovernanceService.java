package com.wcpe.margin;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class MarginGovernanceService {
  public static final String CHANGE_REQUESTS_API =
      "POST /api/v1/tenants/{tenantId}/margin-governance/change-requests";
  public static final String SUBMIT_API = "POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/submit";
  public static final String APPROVE_API = "POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/approve";
  public static final String REJECT_API = "POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/reject";
  public static final String PUBLISH_API = "POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/publish";
  public static final String ROLLBACK_API = "POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/rollback";
  public static final String SUBMIT_PERMISSION = "pricing.governance.submit";
  public static final String APPROVE_PERMISSION = "pricing.governance.approve";
  public static final String PUBLISH_PERMISSION = "pricing.governance.publish";
  public static final String ROLLBACK_PERMISSION = "pricing.governance.rollback";

  public final AtomicInteger marginGovernanceChangeTotal = new AtomicInteger();
  public final AtomicInteger marginGovernanceSlaBreachTotal = new AtomicInteger();
  public final AtomicInteger marginGovernancePublishLatencyMs = new AtomicInteger();

  private final Clock clock;
  private final Map<ChangeKey, MarginGovernanceChangeRequest> changes = new HashMap<>();
  private final Map<String, GovernanceReceipt> idempotencyReceipts = new HashMap<>();
  private final List<Object> outbox = new ArrayList<>();
  private final List<AuditRecord> auditRecords = new ArrayList<>();

  public MarginGovernanceService(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public GovernanceReceipt createChangeRequest(ChangeRequestCommand command) {
    requireCommand(command.tenantId(), command.actorId(), command.idempotencyKey(), command.correlationId());
    requireText(command.targetType(), "targetType");
    requireText(command.targetId(), "targetId");
    requireText(command.targetVersionId(), "targetVersionId");
    requireText(command.diffHash(), "diffHash");
    requireText(command.configHash(), "configHash");
    Objects.requireNonNull(command.approvalRoute(), "approvalRoute is required");
    if (command.approvalRoute().steps().isEmpty()) {
      throw new MarginGovernanceException("APPROVAL_ROUTE_MISSING");
    }
    String idempotencyKey = idempotencyKey(command.tenantId(), command.idempotencyKey());
    GovernanceReceipt existing = idempotencyReceipts.get(idempotencyKey);
    if (existing != null) {
      if (!existing.requestHash().equals(command.requestHash())) {
        throw new MarginGovernanceException("IDEMPOTENCY_CONFLICT");
      }
      return existing;
    }
    String changeId = UUID.randomUUID().toString();
    Instant now = Instant.now(clock);
    MarginGovernanceChangeRequest change = new MarginGovernanceChangeRequest(command.tenantId(), changeId,
        command.targetType(), command.targetId(), command.targetVersionId(), command.expectedVersion(),
        command.configHash(), command.diffHash(), command.riskTier(), ChangeStatus.DRAFT, command.actorId(), null,
        null, now, now, command.approvalRoute(), List.of(), Optional.empty());
    changes.put(new ChangeKey(command.tenantId(), changeId), change);
    marginGovernanceChangeTotal.incrementAndGet();
    auditRecords.add(AuditRecord.recorded(command.tenantId(), changeId, command.actorId(), command.correlationId(),
        "MARGIN_GOVERNANCE_CHANGE_DRAFTED", replayHash(change)));
    GovernanceReceipt receipt = receipt(change, command.correlationId(), command.requestHash(), List.of());
    idempotencyReceipts.put(idempotencyKey, receipt);
    return receipt;
  }

  public GovernanceReceipt submit(String tenantId, String changeId, String actorId, String correlationId,
      SimulationEvidence simulationEvidence) {
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    Objects.requireNonNull(simulationEvidence, "simulationEvidence is required");
    MarginGovernanceChangeRequest change = find(tenantId, changeId);
    if (change.status() != ChangeStatus.DRAFT) {
      throw new MarginGovernanceException("CHANGE_REQUEST_STALE");
    }
    validateSimulation(change, simulationEvidence);
    if (change.approvalRoute().steps().isEmpty()) {
      throw new MarginGovernanceException("APPROVAL_ROUTE_MISSING");
    }
    if (change.highRisk() && !change.approvalRoute().hasStep("COMPLIANCE")) {
      throw new MarginGovernanceException("APPROVAL_ROUTE_MISSING");
    }
    MarginGovernanceChangeRequest submitted = change.withStatus(ChangeStatus.SUBMITTED, now()).withSimulation(simulationEvidence);
    changes.put(new ChangeKey(tenantId, changeId), submitted);
    MarginGovernanceChangeSubmittedEvent event = new MarginGovernanceChangeSubmittedEvent(tenantId, changeId,
        submitted.targetType(), submitted.targetId(), submitted.targetVersionId(), submitted.diffHash(),
        simulationEvidence.simulationHash(), actorId, correlationId, Instant.now(clock));
    outbox.add(event);
    auditRecords.add(AuditRecord.recorded(tenantId, changeId, actorId, correlationId,
        "MARGIN_GOVERNANCE_CHANGE_SUBMITTED", replayHash(submitted)));
    return receipt(submitted, correlationId, "submit:" + changeId, List.of(event));
  }

  public GovernanceReceipt approve(String tenantId, String changeId, ApprovalDecision decision) {
    Objects.requireNonNull(decision, "decision is required");
    requireText(decision.actorId(), "actorId");
    requireText(decision.correlationId(), "correlationId");
    MarginGovernanceChangeRequest change = find(tenantId, changeId);
    if (change.status() != ChangeStatus.SUBMITTED && change.status() != ChangeStatus.PARTIALLY_APPROVED) {
      throw new MarginGovernanceException("CHANGE_REQUEST_STALE");
    }
    if (change.submitterId().equals(decision.actorId())) {
      throw new MarginGovernanceException("SOD_VIOLATION");
    }
    ApprovalStepPolicy expected = change.approvalRoute().nextPendingStep(change.approvals())
        .orElseThrow(() -> new MarginGovernanceException("CHANGE_REQUEST_STALE"));
    if (!expected.step().equals(decision.step())) {
      throw new MarginGovernanceException("APPROVAL_ROUTE_MISSING");
    }
    ApprovalStep approval = new ApprovalStep(UUID.randomUUID().toString(), decision.step(), ApprovalDecisionType.APPROVED,
        decision.actorId(), decision.comments(), decision.evidenceRefs(), Instant.now(clock));
    List<ApprovalStep> approvals = new ArrayList<>(change.approvals());
    approvals.add(approval);
    ChangeStatus status = approvals.size() == change.approvalRoute().steps().size()
        ? ChangeStatus.APPROVED
        : ChangeStatus.PARTIALLY_APPROVED;
    MarginGovernanceChangeRequest approved = change.withApprovals(approvals).withStatus(status, now());
    changes.put(new ChangeKey(tenantId, changeId), approved);
    MarginGovernanceChangeApprovedEvent event = new MarginGovernanceChangeApprovedEvent(tenantId, changeId,
        approved.targetType(), approved.targetId(), approved.targetVersionId(), approved.diffHash(),
        approved.simulationEvidence().map(SimulationEvidence::simulationHash).orElse(""), decision.actorId(),
        decision.correlationId(), Instant.now(clock));
    outbox.add(event);
    auditRecords.add(AuditRecord.recorded(tenantId, changeId, decision.actorId(), decision.correlationId(),
        "MARGIN_GOVERNANCE_CHANGE_APPROVED", replayHash(approved)));
    return receipt(approved, decision.correlationId(), "approve:" + changeId + ":" + decision.step(), List.of(event));
  }

  public GovernanceReceipt reject(String tenantId, String changeId, ApprovalDecision decision) {
    Objects.requireNonNull(decision, "decision is required");
    MarginGovernanceChangeRequest change = find(tenantId, changeId);
    if (change.status() != ChangeStatus.SUBMITTED && change.status() != ChangeStatus.PARTIALLY_APPROVED) {
      throw new MarginGovernanceException("CHANGE_REQUEST_STALE");
    }
    List<ApprovalStep> approvals = new ArrayList<>(change.approvals());
    approvals.add(new ApprovalStep(UUID.randomUUID().toString(), decision.step(), ApprovalDecisionType.REJECTED,
        decision.actorId(), decision.comments(), decision.evidenceRefs(), Instant.now(clock)));
    MarginGovernanceChangeRequest rejected = change.withApprovals(approvals).withStatus(ChangeStatus.REJECTED, now());
    changes.put(new ChangeKey(tenantId, changeId), rejected);
    MarginGovernanceChangeRejectedEvent event = new MarginGovernanceChangeRejectedEvent(tenantId, changeId,
        rejected.targetType(), rejected.targetId(), rejected.targetVersionId(), rejected.diffHash(),
        decision.actorId(), decision.correlationId(), Instant.now(clock));
    outbox.add(event);
    auditRecords.add(AuditRecord.recorded(tenantId, changeId, decision.actorId(), decision.correlationId(),
        "MARGIN_GOVERNANCE_CHANGE_REJECTED", replayHash(rejected)));
    return receipt(rejected, decision.correlationId(), "reject:" + changeId + ":" + decision.step(), List.of(event));
  }

  public GovernanceReceipt publish(String tenantId, String changeId, String actorId, String correlationId,
      int expectedVersion, String configHash) {
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    requireText(configHash, "configHash");
    MarginGovernanceChangeRequest change = find(tenantId, changeId);
    if (change.status() != ChangeStatus.APPROVED || change.expectedVersion() != expectedVersion) {
      throw new MarginGovernanceException("CHANGE_REQUEST_STALE");
    }
    if (!change.configHash().equals(configHash)) {
      throw new MarginGovernanceException("CHANGE_REQUEST_STALE");
    }
    MarginGovernanceChangeRequest published = change.withStatus(ChangeStatus.PUBLISHED, now()).withPublisher(actorId);
    changes.put(new ChangeKey(tenantId, changeId), published);
    MarginGovernanceChangePublishedEvent event = new MarginGovernanceChangePublishedEvent(tenantId, changeId,
        published.targetType(), published.targetId(), published.targetVersionId(), published.diffHash(),
        published.simulationEvidence().map(SimulationEvidence::simulationHash).orElse(""), actorId, correlationId,
        Instant.now(clock));
    outbox.add(event);
    auditRecords.add(AuditRecord.recorded(tenantId, changeId, actorId, correlationId,
        "MARGIN_GOVERNANCE_CHANGE_PUBLISHED", replayHash(published)));
    marginGovernancePublishLatencyMs.set((int) Math.max(0, published.updatedAt().toEpochMilli() - published.createdAt().toEpochMilli()));
    return receipt(published, correlationId, "publish:" + changeId, List.of(event));
  }

  public GovernanceReceipt rollback(String tenantId, String publishedChangeId, RollbackCommand command) {
    Objects.requireNonNull(command, "command is required");
    requireText(command.actorId(), "actorId");
    requireText(command.idempotencyKey(), "idempotencyKey");
    requireText(command.correlationId(), "correlationId");
    requireText(command.priorConfigHash(), "priorConfigHash");
    MarginGovernanceChangeRequest published = find(tenantId, publishedChangeId);
    if (published.status() != ChangeStatus.PUBLISHED || !published.configHash().equals(command.currentConfigHash())) {
      throw new MarginGovernanceException("ROLLBACK_TARGET_INVALID");
    }
    ChangeRequestCommand rollback = new ChangeRequestCommand(tenantId, command.requestId(), command.actorId(),
        command.idempotencyKey(), command.correlationId(), published.targetType(), published.targetId(),
        command.priorVersionId(), published.expectedVersion() + 1, command.priorConfigHash(), command.diffHash(),
        command.riskTier(), command.approvalRoute(), command.requestHash());
    GovernanceReceipt receipt = createChangeRequest(rollback);
    MarginGovernanceChangeRequest created = find(tenantId, receipt.changeId())
        .withRollbackReference(new RollbackReference(publishedChangeId, published.targetVersionId(), command.priorVersionId(),
            command.reasonCode()));
    changes.put(new ChangeKey(tenantId, receipt.changeId()), created);
    auditRecords.add(AuditRecord.recorded(tenantId, receipt.changeId(), command.actorId(), command.correlationId(),
        "MARGIN_GOVERNANCE_ROLLBACK_DRAFTED", replayHash(created)));
    return receipt(created, command.correlationId(), command.requestHash(), List.of());
  }

  public Optional<MarginGovernanceChangeRequest> readChange(String tenantId, String changeId) {
    requireText(tenantId, "tenantId");
    requireText(changeId, "changeId");
    return Optional.ofNullable(changes.get(new ChangeKey(tenantId, changeId)));
  }

  public List<Object> outboxEvents() {
    return List.copyOf(outbox);
  }

  public List<AuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private void validateSimulation(MarginGovernanceChangeRequest change, SimulationEvidence evidence) {
    requireText(evidence.simulationHash(), "simulationHash");
    if (evidence.fixtureRefs() == null || evidence.fixtureRefs().isEmpty()) {
      throw new MarginGovernanceException("SIMULATION_REQUIRED");
    }
    if (!change.diffHash().equals(evidence.diffHash())) {
      throw new MarginGovernanceException("SIMULATION_REQUIRED");
    }
  }

  private MarginGovernanceChangeRequest find(String tenantId, String changeId) {
    requireText(tenantId, "tenantId");
    requireText(changeId, "changeId");
    MarginGovernanceChangeRequest change = changes.get(new ChangeKey(tenantId, changeId));
    if (change == null) {
      throw new MarginGovernanceException("NOT_FOUND");
    }
    return change;
  }

  private GovernanceReceipt receipt(MarginGovernanceChangeRequest change, String correlationId, String requestHash,
      List<Object> events) {
    return new GovernanceReceipt(change.changeId(), change.status(), change.expectedVersion(), correlationId, requestHash,
        List.copyOf(events), "audit:" + change.changeId(), replayHash(change));
  }

  private Instant now() {
    return Instant.now(clock);
  }

  private static void requireCommand(String tenantId, String actorId, String idempotencyKey, String correlationId) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(idempotencyKey, "idempotencyKey");
    requireText(correlationId, "correlationId");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new MarginGovernanceException(field + " is required");
    }
  }

  private static String idempotencyKey(String tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String replayHash(MarginGovernanceChangeRequest change) {
    return Integer.toHexString(Objects.hash(change.tenantId(), change.changeId(), change.targetType(), change.targetId(),
        change.targetVersionId(), change.configHash(), change.diffHash(), change.status(), change.approvals()));
  }

  private record ChangeKey(String tenantId, String changeId) {}

  public enum ChangeStatus { DRAFT, SUBMITTED, PARTIALLY_APPROVED, APPROVED, REJECTED, PUBLISHED }

  public enum ApprovalDecisionType { APPROVED, REJECTED }

  public record ChangeRequestCommand(String tenantId, String requestId, String actorId, String idempotencyKey,
      String correlationId, String targetType, String targetId, String targetVersionId, int expectedVersion,
      String configHash, String diffHash, String riskTier, ApprovalRoute approvalRoute, String requestHash) {}

  public record RollbackCommand(String requestId, String actorId, String idempotencyKey, String correlationId,
      String priorVersionId, String currentConfigHash, String priorConfigHash, String diffHash, String riskTier,
      ApprovalRoute approvalRoute, String reasonCode, String requestHash) {}

  public record ApprovalDecision(String step, String actorId, String comments, List<String> evidenceRefs,
      String correlationId) {
    public ApprovalDecision {
      evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "evidenceRefs is required"));
    }
  }

  public record ApprovalRoute(List<ApprovalStepPolicy> steps) {
    public ApprovalRoute {
      steps = List.copyOf(Objects.requireNonNull(steps, "steps is required"));
    }

    boolean hasStep(String step) {
      return steps.stream().anyMatch(policy -> policy.step().equals(step));
    }

    Optional<ApprovalStepPolicy> nextPendingStep(List<ApprovalStep> approvals) {
      return steps.stream().filter(step -> approvals.stream().noneMatch(done -> done.step().equals(step.step())))
          .findFirst();
    }
  }

  public record ApprovalStepPolicy(String step, String requiredPermission) {
    public ApprovalStepPolicy {
      requireText(step, "step");
      requireText(requiredPermission, "requiredPermission");
    }
  }

  public record SimulationEvidence(String simulationHash, String diffHash, List<String> fixtureRefs,
      List<String> evidenceRefs) {
    public SimulationEvidence {
      fixtureRefs = List.copyOf(Objects.requireNonNull(fixtureRefs, "fixtureRefs is required"));
      evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "evidenceRefs is required"));
    }
  }

  public record ApprovalStep(String approvalId, String step, ApprovalDecisionType decision, String actorId,
      String comments, List<String> evidenceRefs, Instant decidedAt) {}

  public record RollbackReference(String sourceChangeId, String currentVersionId, String rollbackVersionId,
      String reasonCode) {}

  public record MarginGovernanceChangeRequest(String tenantId, String changeId, String targetType, String targetId,
      String targetVersionId, int expectedVersion, String configHash, String diffHash, String riskTier,
      ChangeStatus status, String submitterId, String publisherId, Instant publishedAt, Instant createdAt,
      Instant updatedAt, ApprovalRoute approvalRoute, List<ApprovalStep> approvals,
      Optional<SimulationEvidence> simulationEvidence, Optional<RollbackReference> rollbackReference) {
    MarginGovernanceChangeRequest(String tenantId, String changeId, String targetType, String targetId,
        String targetVersionId, int expectedVersion, String configHash, String diffHash, String riskTier,
        ChangeStatus status, String submitterId, String publisherId, Instant publishedAt, Instant createdAt,
        Instant updatedAt, ApprovalRoute approvalRoute, List<ApprovalStep> approvals,
        Optional<SimulationEvidence> simulationEvidence) {
      this(tenantId, changeId, targetType, targetId, targetVersionId, expectedVersion, configHash, diffHash, riskTier,
          status, submitterId, publisherId, publishedAt, createdAt, updatedAt, approvalRoute, approvals,
          simulationEvidence, Optional.empty());
    }

    public MarginGovernanceChangeRequest {
      approvals = List.copyOf(Objects.requireNonNull(approvals, "approvals is required"));
      simulationEvidence = Objects.requireNonNull(simulationEvidence, "simulationEvidence is required");
      rollbackReference = Objects.requireNonNull(rollbackReference, "rollbackReference is required");
    }

    boolean highRisk() {
      return "HIGH".equalsIgnoreCase(riskTier);
    }

    MarginGovernanceChangeRequest withStatus(ChangeStatus status, Instant now) {
      return new MarginGovernanceChangeRequest(tenantId, changeId, targetType, targetId, targetVersionId,
          expectedVersion, configHash, diffHash, riskTier, status, submitterId, publisherId,
          status == ChangeStatus.PUBLISHED ? now : publishedAt, createdAt, now, approvalRoute, approvals,
          simulationEvidence, rollbackReference);
    }

    MarginGovernanceChangeRequest withApprovals(List<ApprovalStep> approvals) {
      return new MarginGovernanceChangeRequest(tenantId, changeId, targetType, targetId, targetVersionId,
          expectedVersion, configHash, diffHash, riskTier, status, submitterId, publisherId, publishedAt, createdAt,
          updatedAt, approvalRoute, approvals, simulationEvidence, rollbackReference);
    }

    MarginGovernanceChangeRequest withSimulation(SimulationEvidence evidence) {
      return new MarginGovernanceChangeRequest(tenantId, changeId, targetType, targetId, targetVersionId,
          expectedVersion, configHash, diffHash, riskTier, status, submitterId, publisherId, publishedAt, createdAt,
          updatedAt, approvalRoute, approvals, Optional.of(evidence), rollbackReference);
    }

    MarginGovernanceChangeRequest withPublisher(String actorId) {
      return new MarginGovernanceChangeRequest(tenantId, changeId, targetType, targetId, targetVersionId,
          expectedVersion, configHash, diffHash, riskTier, status, submitterId, actorId, publishedAt, createdAt,
          updatedAt, approvalRoute, approvals, simulationEvidence, rollbackReference);
    }

    MarginGovernanceChangeRequest withRollbackReference(RollbackReference reference) {
      return new MarginGovernanceChangeRequest(tenantId, changeId, targetType, targetId, targetVersionId,
          expectedVersion, configHash, diffHash, riskTier, status, submitterId, publisherId, publishedAt, createdAt,
          updatedAt, approvalRoute, approvals, simulationEvidence, Optional.of(reference));
    }
  }

  public record GovernanceReceipt(String changeId, ChangeStatus status, int version, String correlationId,
      String requestHash, List<Object> events, String auditRef, String replayHash) {}

  public record MarginGovernanceChangeSubmittedEvent(String tenantId, String changeId, String targetType,
      String targetId, String targetVersionId, String diffHash, String simulationHash, String actorId,
      String correlationId, Instant occurredAt) {}

  public record MarginGovernanceChangeApprovedEvent(String tenantId, String changeId, String targetType,
      String targetId, String targetVersionId, String diffHash, String simulationHash, String actorId,
      String correlationId, Instant occurredAt) {}

  public record MarginGovernanceChangePublishedEvent(String tenantId, String changeId, String targetType,
      String targetId, String targetVersionId, String diffHash, String simulationHash, String actorId,
      String correlationId, Instant occurredAt) {}

  public record MarginGovernanceChangeRejectedEvent(String tenantId, String changeId, String targetType,
      String targetId, String targetVersionId, String diffHash, String actorId, String correlationId,
      Instant occurredAt) {}

  public record AuditRecord(String tenantId, String changeId, String actorId, String correlationId, String action,
      String replayHash, Instant recordedAt) {
    static AuditRecord recorded(String tenantId, String changeId, String actorId, String correlationId, String action,
        String replayHash) {
      return new AuditRecord(tenantId, changeId, actorId, correlationId, action, replayHash, Instant.now());
    }
  }

  public static final class MarginGovernanceException extends RuntimeException {
    public MarginGovernanceException(String message) {
      super(message);
    }
  }
}
