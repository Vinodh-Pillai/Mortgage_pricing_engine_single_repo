package com.wcpe.governance;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class GovernanceProposalWorkflow {
  public GovernanceValidationResult<GovernanceProposal> createDraft(
      String id, String reason, Map<String, String> proposedEntries) {
    if (isBlank(id)) {
      return GovernanceValidationResult.failure("proposal id is required");
    }
    if (isBlank(reason)) {
      return GovernanceValidationResult.failure("proposal reason is required");
    }
    if (proposedEntries == null || proposedEntries.isEmpty()) {
      return GovernanceValidationResult.failure("proposal entries are required");
    }
    return GovernanceValidationResult.success(
        new GovernanceProposal(id, ProposalState.DRAFT, reason, proposedEntries));
  }

  public GovernanceValidationResult<GovernanceProposal> submit(GovernanceProposal proposal) {
    if (proposal == null) {
      return GovernanceValidationResult.failure("proposal is required");
    }
    if (proposal.state() != ProposalState.DRAFT) {
      return GovernanceValidationResult.failure("only draft proposals can be submitted");
    }
    return GovernanceValidationResult.success(proposal.withState(ProposalState.SUBMITTED));
  }

  public GovernanceValidationResult<GovernanceProposal> compareToBaseline(
      GovernanceProposal proposal, Map<String, String> approvedBaseline) {
    GovernanceValidationResult<GovernanceProposal> proposalValidation = validateProposalData(proposal);
    if (!proposalValidation.valid()) {
      return proposalValidation;
    }
    if (approvedBaseline == null) {
      return GovernanceValidationResult.failure("approved baseline is required");
    }

    var keys = new TreeSet<String>();
    keys.addAll(approvedBaseline.keySet());
    keys.addAll(proposal.proposedEntries().keySet());

    var diffs = new ArrayList<GovernanceProposalDiff>();
    for (String key : keys) {
      boolean baselineHasKey = approvedBaseline.containsKey(key);
      boolean proposalHasKey = proposal.proposedEntries().containsKey(key);
      String baselineValue = approvedBaseline.get(key);
      String proposedValue = proposal.proposedEntries().get(key);

      if (!baselineHasKey && proposalHasKey) {
        diffs.add(new GovernanceProposalDiff(key, DiffType.ADDED, null, proposedValue));
      } else if (baselineHasKey && !proposalHasKey) {
        diffs.add(new GovernanceProposalDiff(key, DiffType.REMOVED, baselineValue, null));
      } else if (!Objects.equals(baselineValue, proposedValue)) {
        diffs.add(new GovernanceProposalDiff(key, DiffType.MODIFIED, baselineValue, proposedValue));
      }
    }

    if (diffs.isEmpty()) {
      return GovernanceValidationResult.failure("proposal must differ from approved baseline");
    }
    return GovernanceValidationResult.success(proposal.withDiffEvidence(diffs));
  }

  public GovernanceValidationResult<GovernanceProposal> approve(GovernanceProposal proposal) {
    GovernanceValidationResult<GovernanceProposal> proposalValidation = validateProposalData(proposal);
    if (!proposalValidation.valid()) {
      return proposalValidation;
    }
    if (proposal.state() != ProposalState.SUBMITTED) {
      return GovernanceValidationResult.failure("only submitted proposals can be approved");
    }
    if (proposal.diffEvidence().isEmpty()) {
      return GovernanceValidationResult.failure("diff evidence is required before approval");
    }
    return GovernanceValidationResult.success(proposal.withState(ProposalState.APPROVED));
  }

  public GovernanceValidationResult<GovernanceProposal> reject(GovernanceProposal proposal) {
    if (proposal == null) {
      return GovernanceValidationResult.failure("proposal is required");
    }
    if (proposal.state() != ProposalState.SUBMITTED) {
      return GovernanceValidationResult.failure("only submitted proposals can be rejected");
    }
    return GovernanceValidationResult.success(proposal.withState(ProposalState.REJECTED));
  }

  private GovernanceValidationResult<GovernanceProposal> validateProposalData(GovernanceProposal proposal) {
    if (proposal == null) {
      return GovernanceValidationResult.failure("proposal is required");
    }
    if (isBlank(proposal.id())) {
      return GovernanceValidationResult.failure("proposal id is required");
    }
    if (isBlank(proposal.reason())) {
      return GovernanceValidationResult.failure("proposal reason is required");
    }
    if (proposal.proposedEntries().isEmpty()) {
      return GovernanceValidationResult.failure("proposal entries are required");
    }
    return GovernanceValidationResult.success(proposal);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
