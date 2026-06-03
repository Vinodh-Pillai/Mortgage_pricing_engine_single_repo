package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record InvestorOverlayProductCandidate(
    UUID productVersionId,
    String productCode,
    UUID investorId,
    String investorCode,
    String channel
) {}
