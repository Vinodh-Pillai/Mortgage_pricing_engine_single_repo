package com.wcpe.mladvisory;

import java.util.List;

public record AdvisoryDisplayPolicy(double collapseBelowConfidence, String disclaimer, List<AllowedAction> allowedActions) {
  public AdvisoryDisplayPolicy {
    allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
  }

  public static AdvisoryDisplayPolicy configured(double collapseBelowConfidence) {
    return new AdvisoryDisplayPolicy(
        collapseBelowConfidence,
        MlAdvisoryControlService.NON_AUTHORITATIVE_DISCLAIMER,
        List.of(AllowedAction.VIEW, AllowedAction.DISMISS, AllowedAction.FEEDBACK));
  }

  public boolean valid() {
    return collapseBelowConfidence >= 0.0
        && collapseBelowConfidence <= 1.0
        && disclaimer != null
        && !disclaimer.isBlank()
        && allowedActions != null
        && allowedActions.contains(AllowedAction.VIEW)
        && allowedActions.stream().allMatch(action -> action == AllowedAction.VIEW || action == AllowedAction.DISMISS || action == AllowedAction.FEEDBACK);
  }

  public boolean collapsedByDefault(double confidence) {
    return confidence < collapseBelowConfidence;
  }
}
