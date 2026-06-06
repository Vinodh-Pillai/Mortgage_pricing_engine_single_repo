package com.wcpe.catalog.domain;

import java.time.Instant;
import java.util.*;

final class ConventionalProductDefinitionPolicy {
  private ConventionalProductDefinitionPolicy() {}

  static void validateStructure(ConventionalProductDraftRequest request) {
    required(request.productCode(), "PRODUCT_CODE_REQUIRED");
    required(request.productName(), "PRODUCT_NAME_REQUIRED");
    required(request.taxonomyTypeCode(), "TAXONOMY_TYPE_REQUIRED");
    required(request.amortizationType(), "AMORTIZATION_TYPE_REQUIRED");
    if (request.minLoanAmount() == null || request.maxLoanAmount() == null) throw new CatalogException("LOAN_AMOUNT_RANGE_REQUIRED");
    if (request.minLoanAmount().scale() > 2 || request.maxLoanAmount().scale() > 2 || request.minLoanAmount().compareTo(request.maxLoanAmount()) > 0) throw new CatalogException("LOAN_AMOUNT_RANGE_INVALID");
    if (request.effectiveEnd() != null && !request.effectiveEnd().isAfter(requiredInstant(request.effectiveStart()))) throw new CatalogException("EFFECTIVE_WINDOW_INVALID");
    if ("FIXED".equals(request.amortizationType())) {
      if (safeInts(request.termMonths()).size() != 1 || request.armIndexCode() != null || request.fixedPeriodMonths() != null || request.adjustmentPeriodMonths() != null) throw new CatalogException("INVALID_FIXED_STRUCTURE");
    } else if ("ARM".equals(request.amortizationType())) {
      if (request.armIndexCode() == null || request.armIndexCode().isBlank()) throw new CatalogException("INVALID_ARM_STRUCTURE");
      if (!Set.of(60, 84, 120).contains(request.fixedPeriodMonths()) || !Integer.valueOf(6).equals(request.adjustmentPeriodMonths())) throw new CatalogException("INVALID_ARM_STRUCTURE");
    } else {
      throw new CatalogException("INVALID_AMORTIZATION_TYPE");
    }
  }

  private static String required(String value, String code) { if (value == null || value.isBlank()) throw new CatalogException(code); return value; }
  private static Instant requiredInstant(Instant value) { if (value == null) throw new CatalogException("EFFECTIVE_START_REQUIRED"); return value; }
  private static List<Integer> safeInts(List<Integer> values) { return values == null ? List.of() : List.copyOf(values); }
}
