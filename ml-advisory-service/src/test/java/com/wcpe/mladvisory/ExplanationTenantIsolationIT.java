package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ExplanationTenantIsolationIT {
  @Test
  void shouldPreventCrossTenantAccess() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-explanation-tenant", 0.66)).value().orElseThrow();

    MlAdvisoryResult<AdvisoryExplanation> result =
        service.getAdvisoryExplanation(
            AdvisoryTestFixtures.OTHER_TENANT,
            card.advisoryId(),
            "pricing-analyst-1",
            Set.of(MlAdvisoryControlService.EXPLANATION_READ_ROLE),
            "pricing-workbench",
            "corr-explanation-tenant");

    assertFalse(result.valid());
    assertEquals("ML_ADVISORY_NOT_FOUND", result.errorCode().orElseThrow());
  }
}
