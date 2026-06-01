package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GovernanceProposalWorkflowTest {
  private final GovernanceProposalWorkflow workflow = new GovernanceProposalWorkflow();

  @Test
  void createsDraftWithoutActivatingProposal() {
    GovernanceValidationResult<GovernanceProposal> result =
        workflow.createDraft("proposal-1", "business reason", Map.of("rule-a", "new"));

    assertTrue(result.valid());
    assertEquals(ProposalState.DRAFT, result.value().orElseThrow().state());
    assertTrue(result.value().orElseThrow().diffEvidence().isEmpty());
  }

  @Test
  void createsDeterministicDiffForAdditionsRemovalsAndModifications() {
    GovernanceProposal proposal =
        workflow
            .createDraft(
                "proposal-1",
                "business reason",
                Map.of("b-modified", "after", "c-added", "new"))
            .value()
            .orElseThrow();

    GovernanceValidationResult<GovernanceProposal> result =
        workflow.compareToBaseline(proposal, Map.of("a-removed", "old", "b-modified", "before"));

    assertTrue(result.valid());
    assertEquals(
        List.of(
            new GovernanceProposalDiff("a-removed", DiffType.REMOVED, "old", null),
            new GovernanceProposalDiff("b-modified", DiffType.MODIFIED, "before", "after"),
            new GovernanceProposalDiff("c-added", DiffType.ADDED, null, "new")),
        result.value().orElseThrow().diffEvidence());
  }

  @Test
  void approvesSubmittedProposalAndPreservesReasonAndDiffEvidence() {
    GovernanceProposal draft =
        workflow.createDraft("proposal-1", "business reason", Map.of("rule-a", "after")).value().orElseThrow();
    GovernanceProposal submitted = workflow.submit(draft).value().orElseThrow();
    GovernanceProposal compared =
        workflow.compareToBaseline(submitted, Map.of("rule-a", "before")).value().orElseThrow();

    GovernanceValidationResult<GovernanceProposal> result = workflow.approve(compared);

    assertTrue(result.valid());
    assertEquals(ProposalState.APPROVED, result.value().orElseThrow().state());
    assertEquals("business reason", result.value().orElseThrow().reason());
    assertEquals(compared.diffEvidence(), result.value().orElseThrow().diffEvidence());
  }

  @Test
  void failsSafelyWhenDraftDataOrBaselineIsMissing() {
    assertFalse(workflow.createDraft("proposal-1", " ", Map.of("rule-a", "after")).valid());
    GovernanceProposal draft =
        workflow.createDraft("proposal-1", "business reason", Map.of("rule-a", "after")).value().orElseThrow();

    GovernanceValidationResult<GovernanceProposal> result = workflow.compareToBaseline(draft, null);

    assertFalse(result.valid());
    assertEquals("approved baseline is required", result.error().orElseThrow());
  }

  @Test
  void failsSafelyForNoOpDiffAndInvalidApprovalTransition() {
    GovernanceProposal draft =
        workflow.createDraft("proposal-1", "business reason", Map.of("rule-a", "same")).value().orElseThrow();

    assertFalse(workflow.compareToBaseline(draft, Map.of("rule-a", "same")).valid());
    assertFalse(workflow.approve(draft).valid());
  }
}
