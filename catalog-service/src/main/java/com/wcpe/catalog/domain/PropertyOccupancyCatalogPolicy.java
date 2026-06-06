package com.wcpe.catalog.domain;

import java.time.Instant;

final class PropertyOccupancyCatalogPolicy {
  private PropertyOccupancyCatalogPolicy() {}

  static void validatePropertyDraft(PropertyTypeDraftRequest request, boolean duplicateCode) {
    if (request == null) throw new CatalogException("PROPERTY_TYPE_REQUEST_REQUIRED");
    required(request.code(), "PROPERTY_TYPE_CODE_REQUIRED");
    required(request.displayName(), "DISPLAY_NAME_REQUIRED");
    if (duplicateCode) throw new CatalogException("PROPERTY_TYPE_CODE_DUPLICATE");
    if (request.effectiveEnd() != null && !request.effectiveEnd().isAfter(requiredInstant(request.effectiveStart()))) throw new CatalogException("EFFECTIVE_WINDOW_INVALID");
    validateUnitRange(request.unitCountMin(), request.unitCountMax());
    if ("CONDO".equals(request.code()) && !Boolean.TRUE.equals(request.requiresProjectReview())) throw new CatalogException("CONDO_REQUIRES_PROJECT_REVIEW");
    if ("TWO_TO_FOUR_UNIT".equals(request.code()) && (!Integer.valueOf(2).equals(request.unitCountMin()) || !Integer.valueOf(4).equals(request.unitCountMax()))) throw new CatalogException("TWO_TO_FOUR_UNIT_RANGE_REQUIRED");
  }

  static void validateOccupancyDraft(OccupancyTypeDraftRequest request, boolean duplicateCode) {
    if (request == null) throw new CatalogException("OCCUPANCY_TYPE_REQUEST_REQUIRED");
    required(request.code(), "OCCUPANCY_TYPE_CODE_REQUIRED");
    required(request.displayName(), "DISPLAY_NAME_REQUIRED");
    if (duplicateCode) throw new CatalogException("OCCUPANCY_TYPE_CODE_DUPLICATE");
    if (request.effectiveEnd() != null && !request.effectiveEnd().isAfter(requiredInstant(request.effectiveStart()))) throw new CatalogException("EFFECTIVE_WINDOW_INVALID");
  }

  private static void validateUnitRange(Integer min, Integer max) {
    if (min == null || max == null) throw new CatalogException("UNIT_RANGE_REQUIRED");
    if (min < 1 || max > 4 || min > max) throw new CatalogException("INVALID_UNIT_RANGE");
  }

  private static String required(String value, String code) { if (value == null || value.isBlank()) throw new CatalogException(code); return value; }
  private static Instant requiredInstant(Instant value) { if (value == null) throw new CatalogException("EFFECTIVE_START_REQUIRED"); return value; }
}
