package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class AdvisoryExplanationControllerContractTest {
  @Test
  void shouldReturnNonAuthoritativeDisclaimer() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-explanation-contract", 0.66)).value().orElseThrow();

    AdvisoryExplanation explanation =
        service
            .getAdvisoryExplanation(
                AdvisoryTestFixtures.TENANT,
                card.advisoryId(),
                "pricing-analyst-1",
                Set.of(MlAdvisoryControlService.EXPLANATION_READ_ROLE),
                "pricing-workbench",
                "corr-explanation-contract")
            .value()
            .orElseThrow();

    assertEquals(MlAdvisoryControlService.GET_ADVISORY_EXPLANATION_ENDPOINT, "GET /api/v1/tenants/{tenantId}/ml-advisory/advisories/{advisoryId}/explanation");
    assertFalse(explanation.authoritative());
    assertTrue(explanation.notAdverseAction());
    assertEquals(MlAdvisoryControlService.NON_AUTHORITATIVE_DISCLAIMER, explanation.disclaimer());
  }

  @Test
  void shouldExposeExplanationGetMappings() throws NoSuchMethodException {
    RestController restController = AdvisoryExplanationController.class.getAnnotation(RestController.class);
    RequestMapping requestMapping = AdvisoryExplanationController.class.getAnnotation(RequestMapping.class);
    Method explanation =
        AdvisoryExplanationController.class.getMethod(
            "explanation", String.class, String.class, String.class, Set.class, String.class, String.class);
    Method auditExport =
        AdvisoryExplanationController.class.getMethod("auditExport", String.class, String.class, String.class, Set.class, String.class);

    assertNotNull(restController);
    assertNotNull(requestMapping);
    assertEquals("/api/v1/tenants/{tenantId}/ml-advisory", requestMapping.value()[0]);
    assertEquals("/advisories/{advisoryId}/explanation", explanation.getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/explanations/{explanationId}/audit-export", auditExport.getAnnotation(GetMapping.class).value()[0]);
  }
}
