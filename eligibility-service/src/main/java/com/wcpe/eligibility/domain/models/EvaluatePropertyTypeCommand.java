package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record EvaluatePropertyTypeCommand(
    UUID scenarioId,
    int scenarioVersion,
    String asOfDate,
    UUID tenantId,
    UUID productVersionId,
    String productCode,
    String investorCode,
    String channel,
    String propertyType,
    int units,
    String occupancyType,
    String loanPurpose,
    String projectReviewStatus
) {}
