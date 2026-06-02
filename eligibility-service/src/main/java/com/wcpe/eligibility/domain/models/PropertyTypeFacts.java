package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record PropertyTypeFacts(
    String propertyType,
    int units,
    String occupancyType,
    String loanPurpose,
    String projectReviewStatus
) {}
