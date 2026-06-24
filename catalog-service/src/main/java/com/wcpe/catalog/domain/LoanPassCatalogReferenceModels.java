package com.wcpe.catalog.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class LoanPassCatalogReferenceModels {
  private LoanPassCatalogReferenceModels() {}

  public record LoanPassProductCatalogRef(
      UUID tenantId,
      String productCode,
      String loanPassProductRef,
      String externalProductType,
      String externalInvestorRef,
      String sourceSystem,
      String sourceProvenance,
      String sourcePayloadRef,
      boolean syntheticDevOnly,
      Map<String, Object> metadata,
      boolean active,
      Instant effectiveStart,
      Instant effectiveEnd) {
    public LoanPassProductCatalogRef {
      if (tenantId == null) throw new CatalogException("TENANT_ID_REQUIRED");
      requireText(productCode, "PRODUCT_CODE_REQUIRED");
      requireText(loanPassProductRef, "LOANPASS_PRODUCT_REF_REQUIRED");
      requireText(sourceSystem, "SOURCE_SYSTEM_REQUIRED");
      requireText(sourceProvenance, "SOURCE_PROVENANCE_REQUIRED");
      validateSyntheticFlag(sourceSystem, syntheticDevOnly);
      validateWindow(effectiveStart, effectiveEnd);
      metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
  }

  public record LoanPassProductAvailabilityRef(
      String availabilityRef,
      UUID tenantId,
      String productCode,
      String investorCode,
      String channelCode,
      String stateCode,
      String availabilityStatus,
      String pricingProfileRef,
      String eligibilityRuleRef,
      String stipulationRuleRef,
      String lockTermSetRef,
      String sourceSystem,
      String sourceProvenance,
      String sourcePayloadRef,
      boolean syntheticDevOnly,
      Map<String, Object> metadata,
      Instant effectiveStart,
      Instant effectiveEnd) {
    public LoanPassProductAvailabilityRef {
      requireText(availabilityRef, "AVAILABILITY_REF_REQUIRED");
      if (tenantId == null) throw new CatalogException("TENANT_ID_REQUIRED");
      requireText(productCode, "PRODUCT_CODE_REQUIRED");
      availabilityStatus = availabilityStatus == null || availabilityStatus.isBlank() ? "CONFIGURED" : availabilityStatus.trim().toUpperCase();
      requireText(sourceSystem, "SOURCE_SYSTEM_REQUIRED");
      requireText(sourceProvenance, "SOURCE_PROVENANCE_REQUIRED");
      validateSyntheticFlag(sourceSystem, syntheticDevOnly);
      validateWindow(effectiveStart, effectiveEnd);
      metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
  }

  private static void requireText(String value, String code) {
    if (value == null || value.isBlank()) throw new CatalogException(code);
  }

  private static void validateWindow(Instant effectiveStart, Instant effectiveEnd) {
    if (effectiveStart != null && effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) {
      throw new CatalogException("EFFECTIVE_WINDOW_INVALID");
    }
  }

  private static void validateSyntheticFlag(String sourceSystem, boolean syntheticDevOnly) {
    if ("SYNTHETIC_DEV".equalsIgnoreCase(sourceSystem) && !syntheticDevOnly) {
      throw new CatalogException("SYNTHETIC_DEV_PROVENANCE_REQUIRED");
    }
  }
}
