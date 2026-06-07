package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdvisoryControllerContractTest {
  @Test
  void shouldReturnRequiredDisclaimerAndAllowedActions() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-contract-s03", 0.81)).value().orElseThrow();

    assertEquals(
        "GET /api/v1/tenants/{tenantId}/ml-advisory/advisories?scenarioId=&pricingResultId=",
        MlAdvisoryControlService.LIST_ADVISORIES_ENDPOINT);
    assertEquals(
        "GET /api/v1/tenants/{tenantId}/ml-advisory/advisories/{advisoryId}",
        MlAdvisoryControlService.GET_ADVISORY_ENDPOINT);
    assertFalse(card.authoritative());
    assertEquals(MlAdvisoryControlService.NON_AUTHORITATIVE_DISCLAIMER, card.disclaimer());
    assertEquals(List.of(AllowedAction.VIEW, AllowedAction.DISMISS, AllowedAction.FEEDBACK), card.allowedActions());
  }
}
