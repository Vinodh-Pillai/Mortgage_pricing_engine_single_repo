package com.wcpe.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

final class TermAmortizationProfilePolicy {
  private static final Set<Integer> FIXED_TERMS = Set.of(120, 180, 240, 360);
  private static final Set<Integer> ARM_INITIAL_FIXED_MONTHS = Set.of(60, 84, 120);
  private static final Set<BigDecimal> ROUNDING_INCREMENTS = Set.of(new BigDecimal("0"), new BigDecimal("12.5"), new BigDecimal("25"));

  private TermAmortizationProfilePolicy() {}

  static void validateDraft(TermAmortizationDraftRequest request, boolean duplicateProfileCode) {
    if (request == null) throw new CatalogException("TERM_AMORTIZATION_REQUEST_REQUIRED");
    required(request.profileCode(), "PROFILE_CODE_REQUIRED");
    required(request.displayName(), "DISPLAY_NAME_REQUIRED");
    required(request.amortizationType(), "AMORTIZATION_TYPE_REQUIRED");
    if (duplicateProfileCode) throw new CatalogException("TERM_AMORTIZATION_CODE_DUPLICATE");
    if (request.effectiveEnd() != null && !request.effectiveEnd().isAfter(requiredInstant(request.effectiveStart()))) throw new CatalogException("EFFECTIVE_WINDOW_INVALID");
    if (Boolean.TRUE.equals(request.interestOnlyAllowed()) || Boolean.TRUE.equals(request.balloonAllowed())) throw new CatalogException("TERM_AMORTIZATION_NOT_SUPPORTED");
    if ("FIXED".equals(request.amortizationType())) validateFixed(request);
    else if ("ARM".equals(request.amortizationType())) validateArm(request);
    else throw new CatalogException("INVALID_AMORTIZATION_TYPE");
  }

  private static void validateFixed(TermAmortizationDraftRequest request) {
    if (!FIXED_TERMS.contains(request.termMonths())) throw new CatalogException("TERM_AMORTIZATION_NOT_SUPPORTED");
    if (request.armIndexCode() != null || request.initialFixedMonths() != null || request.adjustmentPeriodMonths() != null) throw new CatalogException("INVALID_FIXED_STRUCTURE");
    validateOptionalLookbackAndRounding(request);
  }

  private static void validateArm(TermAmortizationDraftRequest request) {
    if (!Integer.valueOf(360).equals(request.termMonths())) throw new CatalogException("TERM_AMORTIZATION_NOT_SUPPORTED");
    if (request.armIndexCode() == null || request.armIndexCode().isBlank()) throw new CatalogException("INVALID_ARM_STRUCTURE");
    if (!ARM_INITIAL_FIXED_MONTHS.contains(request.initialFixedMonths()) || !Integer.valueOf(6).equals(request.adjustmentPeriodMonths())) throw new CatalogException("INVALID_ARM_STRUCTURE");
    validateOptionalLookbackAndRounding(request);
  }

  private static void validateOptionalLookbackAndRounding(TermAmortizationDraftRequest request) {
    if (request.lookbackDays() != null && (request.lookbackDays() < 0 || request.lookbackDays() > 90)) throw new CatalogException("INVALID_LOOKBACK_DAYS");
    if (request.roundingIncrementBps() != null && ROUNDING_INCREMENTS.stream().noneMatch(allowed -> allowed.compareTo(request.roundingIncrementBps()) == 0)) throw new CatalogException("INVALID_ROUNDING_INCREMENT");
  }

  private static String required(String value, String code) { if (value == null || value.isBlank()) throw new CatalogException(code); return value; }
  private static Instant requiredInstant(Instant value) { if (value == null) throw new CatalogException("EFFECTIVE_START_REQUIRED"); return value; }
}
