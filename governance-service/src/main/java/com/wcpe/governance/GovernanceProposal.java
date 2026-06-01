package com.wcpe.governance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GovernanceProposal {
  private final String id;
  private final ProposalState state;
  private final String reason;
  private final Map<String, String> proposedEntries;
  private final List<GovernanceProposalDiff> diffEvidence;

  public GovernanceProposal(String id, ProposalState state, String reason, Map<String, String> proposedEntries) {
    this(id, state, reason, proposedEntries, List.of());
  }

  private GovernanceProposal(
      String id,
      ProposalState state,
      String reason,
      Map<String, String> proposedEntries,
      List<GovernanceProposalDiff> diffEvidence) {
    this.id = id;
    this.state = Objects.requireNonNull(state, "state");
    this.reason = reason;
    this.proposedEntries = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(proposedEntries, "proposedEntries")));
    this.diffEvidence = List.copyOf(Objects.requireNonNull(diffEvidence, "diffEvidence"));
  }

  public String id() {
    return id;
  }

  public ProposalState state() {
    return state;
  }

  public String reason() {
    return reason;
  }

  public Map<String, String> proposedEntries() {
    return proposedEntries;
  }

  public List<GovernanceProposalDiff> diffEvidence() {
    return diffEvidence;
  }

  GovernanceProposal withState(ProposalState nextState) {
    return new GovernanceProposal(id, nextState, reason, proposedEntries, diffEvidence);
  }

  GovernanceProposal withDiffEvidence(List<GovernanceProposalDiff> diffEvidence) {
    return new GovernanceProposal(id, state, reason, proposedEntries, diffEvidence);
  }
}
