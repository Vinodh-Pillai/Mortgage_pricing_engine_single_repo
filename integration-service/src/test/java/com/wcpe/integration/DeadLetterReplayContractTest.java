package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class DeadLetterReplayContractTest {
  @Test
  void dlqFixtureRequiresAuditedReasonForReplayAndDiscardOutcomes() {
    IntegrationContractFixtureSupport.assertContains("dlq/dead-letter-replay-v1.json", "DRY_RUN", "reasonRef", "auditedReasonRequired");
    IntegrationContractFixtureSupport.assertContains("events/integration/dead-letter-replay.schema.json", DeadLetterReplayService.REPLAYED_EVENT_TYPE, DeadLetterReplayService.DISCARDED_EVENT_TYPE, DeadLetterReplayService.BLOCKED_EVENT_TYPE);
  }
}
