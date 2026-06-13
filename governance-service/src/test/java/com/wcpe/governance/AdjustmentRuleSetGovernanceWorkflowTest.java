package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wcpe.governance.AdjustmentRuleSetGovernanceWorkflow.AdjustmentRuleSetCommand;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjustmentRuleSetGovernanceWorkflowTest {
  @Test
  void draftSimulateApprovePublishWorksForAdjustmentRuleSets() {
    AdjustmentRuleSetGovernanceWorkflow workflow = new AdjustmentRuleSetGovernanceWorkflow();
    UUID ruleBookId = UUID.fromString("20000000-0000-0000-0000-000000003201");
    var draft = workflow.draft(new AdjustmentRuleSetCommand(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), ruleBookId, "FNMA_LLPA_2026_06", 5, "analyst", Map.of("source", "rate-feed")));
    var sim = workflow.simulate(draft, Map.of("ficoBandKey", "720-739", "ltvBandKey", "80.01-85", "loanPurpose", "PURCHASE"));
    var approval = workflow.approve(draft, sim, "pricing-admin");
    var publication = workflow.publish(draft, approval);

    assertEquals("DRAFT", draft.status());
    assertEquals("PASSED", sim.status());
    assertEquals("APPROVED", approval.status());
    assertEquals("RuleBookPublished.v1", publication.eventType());
    assertEquals(4, workflow.events().size());
  }
}
