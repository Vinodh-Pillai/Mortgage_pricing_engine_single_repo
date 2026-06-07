package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MlModelVersionApprovalChangedEventTest {
  @Test
  void shouldIncludeGovernanceTicket() {
    ModelRegistryService service = ModelVersionGovernanceFixtures.registryWithVisibleApproval();

    MlAdvisoryOutboxEvent event =
        service.outboxEvents().stream()
            .filter(candidate -> ModelRegistryService.APPROVAL_CHANGED_EVENT.equals(candidate.eventType()))
            .reduce((first, second) -> second)
            .orElseThrow();

    assertEquals("MRM-APPROVE-1", event.payload().get("governanceTicket"));
    assertEquals("ADVISORY_ONLY", event.payload().get("allowedUse"));
  }
}
