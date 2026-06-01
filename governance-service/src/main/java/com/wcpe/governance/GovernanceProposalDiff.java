package com.wcpe.governance;

public record GovernanceProposalDiff(String key, DiffType type, String baselineValue, String proposedValue) {}
