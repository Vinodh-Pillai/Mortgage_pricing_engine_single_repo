package com.wcpe.mladvisory;

import java.util.List;

public final class AdvisoryBoundaryEnforcer {
  private AdvisoryBoundaryEnforcer() {}

  public static boolean authoritative() {
    return false;
  }

  public static List<AllowedAction> allowedDisplayActions(AdvisoryDisplayPolicy policy) {
    if (policy == null || !policy.valid()) {
      return List.of();
    }
    return policy.allowedActions().stream()
        .filter(action -> action == AllowedAction.VIEW || action == AllowedAction.DISMISS || action == AllowedAction.FEEDBACK)
        .distinct()
        .toList();
  }
}
