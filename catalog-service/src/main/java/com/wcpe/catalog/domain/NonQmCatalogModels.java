package com.wcpe.catalog.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

enum NonQmProductType {
  DSCR,
  BANK_STATEMENT,
  ASSET_DEPLETION,
  NO_RATIO,
  FOREIGN_NATIONAL,
  ITIN,
  _1099_ONLY;

  static NonQmProductType fromExternal(String value) {
    if (value == null || value.isBlank()) throw new CatalogException("NON_QM_PRODUCT_TYPE_REQUIRED");
    String normalized = value.trim().toUpperCase().replace('-', '_');
    if ("1099_ONLY".equals(normalized)) normalized = "_1099_ONLY";
    try {
      return NonQmProductType.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      throw new CatalogException("UNKNOWN_NON_QM_PRODUCT_TYPE");
    }
  }

  String externalCode() {
    return this == _1099_ONLY ? "1099_ONLY" : name();
  }
}

record NonQmInvestorChannelMapping(
    String investorCode,
    String channelCode,
    String investorProductCode,
    String status,
    Integer pricingPriority,
    LocalDate effectiveStart,
    LocalDate effectiveEnd) {}

record NonQmProductRequest(
    String productCode,
    String productName,
    String productType,
    Map<String, Object> attributes,
    Map<String, Object> pricingMetadata,
    List<NonQmInvestorChannelMapping> investorMappings,
    String status) {}

record NonQmProductResponse(
    UUID productId,
    String productCode,
    String productName,
    String productFamily,
    String productType,
    Map<String, Object> attributes,
    Map<String, Object> pricingMetadata,
    List<NonQmInvestorChannelMapping> investorMappings,
    List<String> channels,
    String status,
    Instant createdAt,
    Instant updatedAt) {}

record NonQmProductListResponse(List<NonQmProductResponse> products, int count) {}

record NonQmAttributeDefinition(String name, String type, boolean required, String description) {}

record NonQmProductSchema(
    String productFamily,
    String productType,
    String schemaVersion,
    List<NonQmAttributeDefinition> attributes,
    List<String> channels) {}

record NonQmValidationError(String field, String code, String message) {}

record NonQmValidationResult(boolean valid, List<NonQmValidationError> errors) {}

record NonQmProductExport(
    String productCode,
    String productFamily,
    String productType,
    String displayName,
    Map<String, Object> attributes,
    List<NonQmInvestorChannelMapping> investorMappings,
    Map<String, Object> pricingMetadata,
    String version,
    Instant exportedAt) {}

record NonQmImportRequest(List<NonQmProductRequest> products) {}

record NonQmImportResult(int acceptedRows, int rejectedRows, List<String> rejectedProductCodes) {}
