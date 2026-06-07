package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelLifecyclePolicyTest {
  @Test
  void shouldBlockInvalidStatusTransition() {
    ModelLifecyclePolicy policy = new ModelLifecyclePolicy();

    assertFalse(policy.canApprove(ModelStatus.DRAFT, ModelStatus.APPROVED_ADVISORY_VISIBLE));
    assertFalse(policy.canSuspend(ModelStatus.IN_REVIEW));
    assertTrue(policy.canApprove(ModelStatus.IN_REVIEW, ModelStatus.APPROVED_SHADOW));
  }
}
