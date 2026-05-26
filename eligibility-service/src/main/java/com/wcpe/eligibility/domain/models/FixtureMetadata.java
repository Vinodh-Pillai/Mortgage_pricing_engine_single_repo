package com.wcpe.eligibility.domain.models;

import java.time.LocalDate;
import java.util.List;

/**
 * Synthetic fixture metadata per LLD governance spec.
 * Must record nonProduction true and approvedForProduction false.
 */
public record FixtureMetadata(
    String fixtureId,
    String fixtureVersion,
    String seed,
    String scope,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean nonProduction,
    boolean approvedForProduction,
    String requirementPurpose,
    List<String> allowedStatusesCovered,
    boolean doNotUseForProductionPricing
) {}
