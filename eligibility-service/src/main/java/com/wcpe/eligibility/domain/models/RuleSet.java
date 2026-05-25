package com.wcpe.eligibility.domain.models;

import java.util.List;

public record RuleSet(
    String name,
    String version,
    List<String> ruleCodes,
    boolean deterministic
) {}
