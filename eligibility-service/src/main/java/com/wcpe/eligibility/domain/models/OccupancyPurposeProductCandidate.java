package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record OccupancyPurposeProductCandidate(
    UUID productVersionId,
    String productCode,
    String investorCode,
    String channel
) {}
