package com.wcpe.eligibility.domain.models;

public record OccupancyPurposeFacts(
    String loanPurpose,
    String occupancyType,
    int units,
    String propertyType
) {}
