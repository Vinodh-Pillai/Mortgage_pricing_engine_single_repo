package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.UUID;

public record InvestorOverlayRuleSetConfig(
    UUID overlaySetId,
    String investorId,
    String productVersionId,
    String channel,
    int version,
    int precedence,
    List<InvestorOverlayRuleRow> rows
) {}
