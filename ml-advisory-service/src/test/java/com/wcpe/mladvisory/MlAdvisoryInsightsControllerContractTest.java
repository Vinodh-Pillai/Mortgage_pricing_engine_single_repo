package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class MlAdvisoryInsightsControllerContractTest {

  @Test
  void exposesAdvisoryInsightsWithoutApplyingPricingDecisions() throws NoSuchMethodException {
    RestController restController = MlAdvisoryInsightsController.class.getAnnotation(RestController.class);
    RequestMapping requestMapping = MlAdvisoryInsightsController.class.getAnnotation(RequestMapping.class);
    GetMapping getMapping = MlAdvisoryInsightsController.class
        .getDeclaredMethod("insights", String.class, String.class)
        .getAnnotation(GetMapping.class);
    MlAdvisoryInsightsController.MlAdvisoryInsightsView view = new MlAdvisoryInsightsController()
        .insights("demo-tenant", "trace-ml-s14");

    assertNotNull(restController);
    assertNotNull(requestMapping);
    assertNotNull(getMapping);
    assertEquals("/api/v1/tenants/{tenantId}/ml-advisory/insights", requestMapping.value()[0]);
    assertEquals("demo-tenant", view.tenantContext());
    assertEquals("model-version-ref-required", view.recommendations().get(0).modelVersion());
    assertEquals("confidence-score-from-model-output", view.recommendations().get(0).confidence());
    assertEquals("VIEW_EXPLANATION", view.recommendations().get(0).allowedActions().get(0));
    assertEquals("audit-ref-required", view.recommendations().get(0).auditRefs().get(0));
    assertFalse(view.recommendations().get(0).automaticDecisionApplied());
    assertEquals("DRIFT_BASELINE_REQUIRED", view.modelVersions().get(0).driftStatus());
    assertEquals("ALERT_REVIEW_REQUIRED", view.modelVersions().get(0).alertState());
    assertTrue(view.advisoryUnavailable());
    assertEquals("trace-ml-s14", view.uiTraceId());
  }
}
