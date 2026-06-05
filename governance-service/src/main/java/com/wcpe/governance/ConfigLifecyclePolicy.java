package com.wcpe.governance;

import java.time.Duration;
import java.util.Map;

public record ConfigLifecyclePolicy(
    String policyVersionId,
    boolean allowEmergencySelfApproval,
    boolean requireSecondApprovalForEmergency,
    boolean allowAutoPublish,
    boolean allowActivePrecedenceOverlap,
    Duration validationFreshness,
    Map<String, String> requiredApprovalGroups) {
  public ConfigLifecyclePolicy {
    requiredApprovalGroups = Map.copyOf(requiredApprovalGroups == null ? Map.of() : requiredApprovalGroups);
  }
}
