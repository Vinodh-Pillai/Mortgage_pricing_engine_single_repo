package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record PropertyTypeProductCandidate(
    UUID productVersionId,
    String productCode,
    String investorCode,
    String channel
) {}
