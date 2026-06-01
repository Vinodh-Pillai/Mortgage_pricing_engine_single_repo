package com.wcpe.catalog.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

class CatalogContractService {
  CatalogContractResponse lookup(CatalogContractRequest request) {
    validate(request);

    String status = request.featureEnabled() ? "CONTRACT_AVAILABLE" : "FEATURE_DISABLED";
    return new CatalogContractResponse(
        request.productKey().trim(),
        request.investorProgramKey() == null ? null : request.investorProgramKey().trim(),
        status,
        List.of("catalog-contract-skeleton"));
  }

  private static void validate(CatalogContractRequest request) {
    if (request == null) throw new CatalogException("CATALOG_CONTRACT_REQUEST_REQUIRED");
    requireIdentifier(request.productKey(), "INVALID_PRODUCT_IDENTIFIER");
    if (request.investorProgramKey() != null && !request.investorProgramKey().isBlank()) {
      requireIdentifier(request.investorProgramKey(), "INVALID_INVESTOR_IDENTIFIER");
    }
  }

  private static void requireIdentifier(String value, String errorCode) {
    if (value == null || value.isBlank() || !value.trim().matches("[A-Z0-9][A-Z0-9_-]{1,63}")) {
      throw new CatalogException(errorCode);
    }
  }
}

record CatalogContractRequest(
    String productKey,
    String investorProgramKey,
    String tenantKey,
    String channel,
    LocalDate effectiveDate,
    boolean featureEnabled) {}

record CatalogContractResponse(
    String productKey,
    String investorProgramKey,
    String status,
    List<String> contractCapabilities) {
  CatalogContractResponse {
    contractCapabilities = List.copyOf(Objects.requireNonNull(contractCapabilities));
  }
}
