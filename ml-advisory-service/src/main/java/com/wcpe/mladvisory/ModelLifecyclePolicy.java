package com.wcpe.mladvisory;

final class ModelLifecyclePolicy {
  boolean canSubmitReview(ModelStatus current) {
    return current == ModelStatus.DRAFT || current == ModelStatus.REMEDIATION_REQUIRED;
  }

  boolean canApprove(ModelStatus current, ModelStatus target) {
    return current == ModelStatus.IN_REVIEW
        && (target == ModelStatus.APPROVED_SHADOW || target == ModelStatus.APPROVED_ADVISORY_VISIBLE);
  }

  boolean canSuspend(ModelStatus current) {
    return current == ModelStatus.APPROVED_SHADOW || current == ModelStatus.APPROVED_ADVISORY_VISIBLE;
  }

  boolean discoverable(ModelStatus status) {
    return status == ModelStatus.APPROVED_SHADOW || status == ModelStatus.APPROVED_ADVISORY_VISIBLE;
  }
}
