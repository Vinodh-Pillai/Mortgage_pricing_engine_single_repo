package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class EligibilityRiskAdvisoryControllerContractTest {
  @Test
  void shouldReturnEligibilityUnchanged() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.eligibilityRiskServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();

    EligibilityRiskAdvisoryEvaluation evaluation =
        service
            .evaluateEligibilityRiskAdvisory(
                AdvisoryTestFixtures.eligibilityRiskCommand("idem-eligibility-contract", snapshotId), new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    assertEquals(
        "POST /api/v1/tenants/{tenantId}/ml-advisory/eligibility-risk:evaluate",
        MlAdvisoryControlService.EVALUATE_ELIGIBILITY_RISK_ADVISORY_ENDPOINT);
    assertFalse(evaluation.authoritative());
    assertTrue(evaluation.notAdverseAction());
    assertTrue(evaluation.eligibilityUnchanged());
    assertTrue(evaluation.disclaimer().contains("not adverse action"));
    assertTrue(evaluation.feedbackUrl().contains("/ml-advisory/eligibility-risk/"));
    assertTrue(evaluation.explanationUrl().contains("/ml-advisory/eligibility-risk/"));
  }

  @Test
  void shouldExposeEligibilityRiskMappings() throws NoSuchMethodException {
    RestController restController = EligibilityRiskAdvisoryController.class.getAnnotation(RestController.class);
    RequestMapping requestMapping = EligibilityRiskAdvisoryController.class.getAnnotation(RequestMapping.class);
    Method evaluate =
        EligibilityRiskAdvisoryController.class.getMethod(
            "evaluate", String.class, EligibilityRiskAdvisoryController.EligibilityRiskAdvisoryEvaluationRequest.class);
    Method get = EligibilityRiskAdvisoryController.class.getMethod("get", String.class, String.class);
    PostMapping postMapping = evaluate.getAnnotation(PostMapping.class);
    GetMapping getMapping = get.getAnnotation(GetMapping.class);

    assertNotNull(restController);
    assertNotNull(requestMapping);
    assertEquals("/api/v1/tenants/{tenantId}/ml-advisory/eligibility-risk", requestMapping.value()[0]);
    assertNotNull(postMapping);
    assertEquals(":evaluate", postMapping.value()[0]);
    assertNotNull(getMapping);
    assertEquals("/{advisoryId}", getMapping.value()[0]);
  }
}
