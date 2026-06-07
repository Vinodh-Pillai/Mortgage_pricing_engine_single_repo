package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class PricingAdvisoryControllerContractTest {
  @Test
  void shouldReturnAuthoritativeFalseAndDisclaimer() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.pricingServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();

    PricingAdvisoryEvaluation evaluation =
        service
            .evaluatePricingAdvisory(
                AdvisoryTestFixtures.pricingEvaluationCommand("idem-pricing-contract", 0.82, snapshotId, false, false, false),
                new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    assertEquals(
        "POST /api/v1/tenants/{tenantId}/ml-advisory/pricing-advisories:evaluate",
        MlAdvisoryControlService.EVALUATE_PRICING_ADVISORY_ENDPOINT);
    assertFalse(evaluation.authoritative());
    assertTrue(evaluation.deterministicPricingUnchanged());
    assertEquals(MlAdvisoryControlService.NON_AUTHORITATIVE_DISCLAIMER, evaluation.disclaimer());
    assertTrue(evaluation.feedbackUrl().contains("/ml-advisory/pricing-advisories/"));
    assertTrue(evaluation.explanationUrl().contains("/ml-advisory/pricing-advisories/"));
  }

  @Test
  void shouldExposeRuntimePostMapping() throws NoSuchMethodException {
    RestController restController = PricingAdvisoryController.class.getAnnotation(RestController.class);
    RequestMapping requestMapping = PricingAdvisoryController.class.getAnnotation(RequestMapping.class);
    Method evaluate =
        PricingAdvisoryController.class.getMethod(
            "evaluate", String.class, PricingAdvisoryController.PricingAdvisoryEvaluationRequest.class);
    PostMapping postMapping = evaluate.getAnnotation(PostMapping.class);

    assertNotNull(restController);
    assertNotNull(requestMapping);
    assertEquals("/api/v1/tenants/{tenantId}/ml-advisory/pricing-advisories", requestMapping.value()[0]);
    assertNotNull(postMapping);
    assertEquals(":evaluate", postMapping.value()[0]);
  }
}
