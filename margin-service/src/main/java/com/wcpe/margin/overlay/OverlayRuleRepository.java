package com.wcpe.margin.overlay;

import java.util.List;

public interface OverlayRuleRepository {
  List<OverlayRule> findApplicable(OverlayInputs inputs);

  static OverlayRuleRepository empty() {
    return inputs -> {
      throw new IllegalStateException("OverlayRuleRepository:PERSISTENT_STORE_REQUIRED");
    };
  }
}
