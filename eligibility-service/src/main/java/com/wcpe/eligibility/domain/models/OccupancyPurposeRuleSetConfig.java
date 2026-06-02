package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.UUID;

public record OccupancyPurposeRuleSetConfig(
    UUID ruleSetId,
    String productFamily,
    String investorCode,
    String channel,
    int precedence,
    List<OccupancyPurposeRuleRow> rows
) {}
