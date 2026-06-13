package com.wcpe.governance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Local workflow adapter for adjustment rule sets produced by the rate-feed LLPA mapper. */
public final class AdjustmentRuleSetGovernanceWorkflow {
  private final List<AdjustmentRuleSetEvent> events = new ArrayList<>();

  public AdjustmentRuleSetDraft draft(AdjustmentRuleSetCommand command) {
    validate(command);
    AdjustmentRuleSetDraft draft = new AdjustmentRuleSetDraft(command.tenantId(), command.ruleBookId(), command.businessKey(), "DRAFT", command.ruleCount(), Instant.now(), command.actorId(), command.metadata());
    events.add(new AdjustmentRuleSetEvent("RuleBookDraftCreated.v1", draft.ruleBookId(), draft.status(), draft.createdAt(), draft.actorId()));
    return draft;
  }

  public AdjustmentRuleSetSimulation simulate(AdjustmentRuleSetDraft draft, Map<String, String> factsByDimension) {
    Objects.requireNonNull(draft, "draft is required");
    if (factsByDimension == null || factsByDimension.isEmpty()) throw new IllegalArgumentException("sample facts are required before approval");
    AdjustmentRuleSetSimulation simulation = new AdjustmentRuleSetSimulation(draft.ruleBookId(), "PASSED", factsByDimension, Instant.now());
    events.add(new AdjustmentRuleSetEvent("RuleBookSimulationCompleted.v1", draft.ruleBookId(), simulation.status(), simulation.completedAt(), "governance-service"));
    return simulation;
  }

  public AdjustmentRuleSetApproval approve(AdjustmentRuleSetDraft draft, AdjustmentRuleSetSimulation simulation, String approverId) {
    Objects.requireNonNull(draft, "draft is required");
    Objects.requireNonNull(simulation, "simulation is required");
    if (!"PASSED".equals(simulation.status())) throw new IllegalStateException("simulation must pass before approval");
    if (approverId == null || approverId.isBlank()) throw new IllegalArgumentException("approverId is required");
    if (approverId.equals(draft.actorId())) throw new IllegalArgumentException("submitter cannot approve the same adjustment rule set");
    AdjustmentRuleSetApproval approval = new AdjustmentRuleSetApproval(draft.ruleBookId(), "APPROVED", approverId, Instant.now());
    events.add(new AdjustmentRuleSetEvent("RuleBookApproved.v1", draft.ruleBookId(), approval.status(), approval.approvedAt(), approverId));
    return approval;
  }

  public AdjustmentRuleSetPublication publish(AdjustmentRuleSetDraft draft, AdjustmentRuleSetApproval approval) {
    Objects.requireNonNull(draft, "draft is required");
    Objects.requireNonNull(approval, "approval is required");
    if (!"APPROVED".equals(approval.status())) throw new IllegalStateException("approval is required before publish");
    AdjustmentRuleSetPublication publication = new AdjustmentRuleSetPublication(draft.ruleBookId(), "PUBLISHED", Instant.now(), "RuleBookPublished.v1");
    events.add(new AdjustmentRuleSetEvent(publication.eventType(), draft.ruleBookId(), publication.status(), publication.publishedAt(), approval.approverId()));
    return publication;
  }

  public List<AdjustmentRuleSetEvent> events() {
    return List.copyOf(events);
  }

  private static void validate(AdjustmentRuleSetCommand command) {
    Objects.requireNonNull(command, "command is required");
    Objects.requireNonNull(command.tenantId(), "tenantId is required");
    Objects.requireNonNull(command.ruleBookId(), "ruleBookId is required");
    if (command.businessKey() == null || command.businessKey().isBlank()) throw new IllegalArgumentException("businessKey is required");
    if (command.ruleCount() <= 0) throw new IllegalArgumentException("at least one mapped adjustment rule is required");
    if (command.actorId() == null || command.actorId().isBlank()) throw new IllegalArgumentException("actorId is required");
  }

  public record AdjustmentRuleSetCommand(UUID tenantId, UUID ruleBookId, String businessKey, int ruleCount, String actorId, Map<String, String> metadata) {}
  public record AdjustmentRuleSetDraft(UUID tenantId, UUID ruleBookId, String businessKey, String status, int ruleCount, Instant createdAt, String actorId, Map<String, String> metadata) {}
  public record AdjustmentRuleSetSimulation(UUID ruleBookId, String status, Map<String, String> factsByDimension, Instant completedAt) {}
  public record AdjustmentRuleSetApproval(UUID ruleBookId, String status, String approverId, Instant approvedAt) {}
  public record AdjustmentRuleSetPublication(UUID ruleBookId, String status, Instant publishedAt, String eventType) {}
  public record AdjustmentRuleSetEvent(String eventType, UUID ruleBookId, String status, Instant occurredAt, String actorId) {}
}
