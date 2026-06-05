package com.wcpe.governance;

import java.util.List;

public record ConfigLifecycleResult(
    ConfigLifecycleVersion version,
    ApprovalRequest approvalRequest,
    ApprovalDecision approvalDecision,
    PublishSchedule publishSchedule,
    LifecycleTransition transition,
    ConfigApiAuditRecord auditRecord,
    ConfigApiOutboxEvent outboxEvent,
    List<ConfigLifecycleVersion> affectedPublishedVersions,
    String auditRef,
    String replayRef) {
  public ConfigLifecycleResult {
    affectedPublishedVersions = List.copyOf(affectedPublishedVersions == null ? List.of() : affectedPublishedVersions);
  }
}
