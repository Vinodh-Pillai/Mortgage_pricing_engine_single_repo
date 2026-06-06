package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarginReplayIT {
  @Test
  void replaysHistoricalCompanyChannelBranchComp() {
    var service = new MarginReplayService(MarginReplayTestFixtures.CLOCK);
    var fixture = MarginReplayTestFixtures.fullStackFixture();
    service.registerFixture(MarginReplayTestFixtures.TENANT, fixture);

    var result = service.runFixtureReplay(
        MarginReplayTestFixtures.fullStackCommand(MarginReplayTestFixtures.fullStackManifest()));

    assertEquals("MATCH", result.matchStatus());
    assertEquals(fixture.expectedResultHash(), result.actualHash());
    assertTrue(service.replayRun(MarginReplayTestFixtures.TENANT, result.replayId()).isPresent());
    assertEquals(1, service.marginReplayRunTotal.get());
  }

  @Test
  void classifiesHashMismatchByStep() {
    var service = new MarginReplayService(MarginReplayTestFixtures.CLOCK);
    service.registerFixture(MarginReplayTestFixtures.TENANT, MarginReplayTestFixtures.fullStackFixture());
    var changedManifest = new MarginReplayService.VersionManifest(
        MarginReplayTestFixtures.fullStackManifest().manifestId(),
        MarginReplayTestFixtures.fullStackManifest().policyVersionRefs(),
        MarginReplayTestFixtures.fullStackSteps("company-margin-hash", "channel-margin-hash",
            "branch-overlay-changed-hash", "lo-comp-hash", "broker-comp-hash", "profitability-floor-hash"),
        MarginReplayTestFixtures.fullStackManifest().eventsObserved(),
        MarginReplayTestFixtures.fullStackManifest().runEnvironment());

    var result = service.runFixtureReplay(MarginReplayTestFixtures.fullStackCommand(changedManifest));

    assertEquals("MISMATCH", result.matchStatus());
    assertEquals("BRANCH_OVERLAY", result.mismatches().get(0).stepType());
    assertEquals("STEP_HASH_MISMATCH", result.mismatches().get(0).classification());
    assertEquals(1, service.marginReplayHashMismatchTotal.get());
  }

  @Test
  void auditEvidenceContainsVersionManifest() {
    var service = new MarginReplayService(MarginReplayTestFixtures.CLOCK);
    service.registerFixture(MarginReplayTestFixtures.TENANT, MarginReplayTestFixtures.fullStackFixture());

    var result = service.runFixtureReplay(
        MarginReplayTestFixtures.fullStackCommand(MarginReplayTestFixtures.fullStackManifest()));

    var evidence = service.auditPackages().stream()
        .filter(item -> result.auditEvidenceId().equals(item.evidenceId()))
        .findFirst()
        .orElseThrow();
    assertEquals("manifest-margin-comp-full-stack", evidence.versionManifest().manifestId());
    assertTrue(evidence.excludesBorrowerPii());
    assertFalse(evidence.calculationLedger().isEmpty());
  }
}
