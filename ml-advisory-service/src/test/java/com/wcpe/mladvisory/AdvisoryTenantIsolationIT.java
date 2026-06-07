package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdvisoryTenantIsolationIT {
  @Test
  void shouldRejectCrossTenantAdvisoryRead() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-tenant-isolation", 0.74)).value().orElseThrow();

    MlAdvisoryResult<AdvisoryCard> crossTenant =
        service.getAdvisoryDetails(
            AdvisoryTestFixtures.OTHER_TENANT,
            card.advisoryId(),
            "pricing-analyst-2",
            "pricing-workbench",
            "corr-cross-tenant");

    assertTrue(crossTenant.errorCode().isPresent());
    assertEquals("ML_ADVISORY_NOT_FOUND", crossTenant.errorCode().orElseThrow());
  }
}
