package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record ProductCandidate(
    UUID productVersionId,
    String productCode,
    String investorCode
) {}
