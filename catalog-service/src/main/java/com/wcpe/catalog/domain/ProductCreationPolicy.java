package com.wcpe.catalog.domain;

import java.time.Instant;
import java.util.*;

final class ProductCreationPolicy {
  private static final Set<String> ALLOWED_STATUSES = Set.of("DRAFT", "ACTIVE");

  private ProductCreationPolicy() {}

  static ProductCreationDraft validate(ProductCreationRequest request) {
    if (request == null) throw new CatalogException("PRODUCT_CREATION_REQUEST_REQUIRED");
    String productCode = required(request.productCode(), "PRODUCT_CODE_REQUIRED").trim().toUpperCase(Locale.ROOT);
    String displayName = required(request.displayName(), "PRODUCT_NAME_REQUIRED").trim();
    String productFamily = required(request.productFamily(), "PRODUCT_FAMILY_REQUIRED").trim().toUpperCase(Locale.ROOT);
    String productType = required(request.productType(), "PRODUCT_TYPE_REQUIRED").trim().toUpperCase(Locale.ROOT);
    List<Integer> supportedTerms = positiveTerms(request.supportedTerms());
    List<String> amortizationTypes = nonBlankUpper(request.amortizationTypes(), "AMORTIZATION_TYPES_REQUIRED");
    List<String> loanPurposes = nonBlankUpper(request.loanPurposes(), "LOAN_PURPOSES_REQUIRED");
    List<String> supportedChannels = nonBlankUpper(request.supportedChannels(), "SUPPORTED_CHANNELS_REQUIRED");
    List<String> allowedStates = nonBlankUpper(request.allowedStates(), "ALLOWED_STATES_REQUIRED");
    Instant effectiveStart = requiredInstant(request.effectiveStart());
    Instant effectiveEnd = request.effectiveEnd();
    if (effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) throw new CatalogException("EFFECTIVE_WINDOW_INVALID");
    String status = request.status() == null || request.status().isBlank() ? "DRAFT" : request.status().trim().toUpperCase(Locale.ROOT);
    if (!ALLOWED_STATUSES.contains(status)) throw new CatalogException("INVALID_PRODUCT_STATUS");
    return new ProductCreationDraft(productCode, displayName, productFamily, productType, supportedTerms, amortizationTypes,
        loanPurposes, supportedChannels, allowedStates, metadataRefs(request.metadataRefs()), effectiveStart, effectiveEnd, status);
  }

  private static List<Integer> positiveTerms(List<Integer> terms) {
    if (terms == null || terms.isEmpty()) throw new CatalogException("SUPPORTED_TERMS_REQUIRED");
    List<Integer> normalized = terms.stream().filter(Objects::nonNull).distinct().sorted().toList();
    if (normalized.size() != terms.size() || normalized.stream().anyMatch(term -> term <= 0)) throw new CatalogException("INVALID_SUPPORTED_TERM");
    return normalized;
  }

  private static List<String> nonBlankUpper(List<String> values, String missingCode) {
    if (values == null || values.isEmpty()) throw new CatalogException(missingCode);
    List<String> normalized = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) throw new CatalogException(missingCode);
      normalized.add(value.trim().toUpperCase(Locale.ROOT));
    }
    return normalized.stream().distinct().sorted().toList();
  }

  private static Map<String, Object> metadataRefs(Map<String, Object> refs) {
    if (refs == null || refs.isEmpty()) return Map.of();
    Map<String, Object> normalized = new TreeMap<>();
    for (Map.Entry<String, Object> entry : refs.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) throw new CatalogException("MAPPING_METADATA_KEY_REQUIRED");
      if (entry.getValue() == null) throw new CatalogException("MAPPING_METADATA_VALUE_REQUIRED");
      normalized.put(entry.getKey().trim(), entry.getValue());
    }
    return Map.copyOf(normalized);
  }

  private static String required(String value, String code) { if (value == null || value.isBlank()) throw new CatalogException(code); return value; }
  private static Instant requiredInstant(Instant value) { if (value == null) throw new CatalogException("EFFECTIVE_START_REQUIRED"); return value; }
}
