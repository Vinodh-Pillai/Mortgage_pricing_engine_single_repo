package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record FicoLtvProductCandidate(
    UUID productVersionId,
    String productCode,
    String investorCode
) {}
