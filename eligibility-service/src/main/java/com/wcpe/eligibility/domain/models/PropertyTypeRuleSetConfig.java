package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.UUID;

public record PropertyTypeRuleSetConfig(
    UUID ruleSetId,
    String tenantId,
    String productFamily,
    String productCode,
    String investorCode,
    String channel,
    int version,
    int precedence,
    List<PropertyTypeRuleRow> rows
) {}
