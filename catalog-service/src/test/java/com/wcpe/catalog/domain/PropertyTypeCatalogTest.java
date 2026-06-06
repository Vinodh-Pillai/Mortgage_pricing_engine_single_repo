package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropertyTypeCatalogTest {
  @Test
  void condoRequiresProjectReview() {
    assertThatThrownBy(() -> PropertyOccupancyCatalogPolicy.validatePropertyDraft(new PropertyTypeDraftRequest(
        "CONDO", "Condominium", "PROPERTY", List.of("CONDOMINIUM"), true, false, 1, 1, Instant.parse("2026-01-01T00:00:00Z"), null), false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("CONDO_REQUIRES_PROJECT_REVIEW");
  }

  @Test
  void twoToFourUnitRequiresRangeTwoThroughFour() {
    assertThatThrownBy(() -> PropertyOccupancyCatalogPolicy.validatePropertyDraft(new PropertyTypeDraftRequest(
        "TWO_TO_FOUR_UNIT", "2-4 Unit", "PROPERTY", List.of(), true, false, 1, 4, Instant.parse("2026-01-01T00:00:00Z"), null), false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("TWO_TO_FOUR_UNIT_RANGE_REQUIRED");
  }

  @Test
  void acceptsStoryCitedPropertyAndOccupancyDrafts() {
    PropertyOccupancyCatalogPolicy.validatePropertyDraft(new PropertyTypeDraftRequest(
        "CONDO", "Condominium", "PROPERTY", List.of("CONDOMINIUM"), true, true, 1, 1, Instant.parse("2026-01-01T00:00:00Z"), null), false);
    PropertyOccupancyCatalogPolicy.validatePropertyDraft(new PropertyTypeDraftRequest(
        "TWO_TO_FOUR_UNIT", "2-4 Unit", "PROPERTY", List.of("2_UNIT", "3_UNIT", "4_UNIT"), true, false, 2, 4, Instant.parse("2026-01-01T00:00:00Z"), null), false);
    PropertyOccupancyCatalogPolicy.validateOccupancyDraft(new OccupancyTypeDraftRequest(
        "SECOND_HOME", "Second Home", List.of("SECONDARY_RESIDENCE"), true, Instant.parse("2026-01-01T00:00:00Z"), null), false);
  }
}
