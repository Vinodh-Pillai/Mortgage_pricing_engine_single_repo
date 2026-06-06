package com.wcpe.catalog.domain;

import java.util.*;
import java.util.function.BiPredicate;

final class ChannelTaxonomyPolicy {
  static final Set<String> BASELINE_CODES = Set.of("RETAIL", "WHOLESALE", "CORRESPONDENT", "CONSUMER_DIRECT", "PARTNER_API");

  private ChannelTaxonomyPolicy() {}

  static void validateDraft(ChannelTaxonomyDraftRequest request, boolean duplicateCode, BiPredicate<String, String> mappingExists) {
    if (request == null) throw new CatalogException("CHANNEL_REQUEST_REQUIRED");
    String code = required(request.channelCode(), "CHANNEL_CODE_REQUIRED");
    if (!code.matches("[A-Z][A-Z0-9_]{1,39}")) throw new CatalogException("CHANNEL_CODE_INVALID");
    if (!BASELINE_CODES.contains(code)) throw new CatalogException("CHANNEL_CODE_NOT_BASELINE");
    if (duplicateCode) throw new CatalogException("CHANNEL_CODE_DUPLICATE");
    required(request.displayName(), "CHANNEL_DISPLAY_NAME_REQUIRED");
    required(request.defaultMarginGroupCode(), "DEFAULT_MARGIN_GROUP_REQUIRED");
    if (request.effectiveStart() == null) throw new CatalogException("EFFECTIVE_START_REQUIRED");
    if (request.effectiveEnd() != null && !request.effectiveEnd().isAfter(request.effectiveStart())) throw new CatalogException("CHANNEL_EFFECTIVE_WINDOW_INVALID");
    if (request.allowedSourceSystems() == null || request.allowedSourceSystems().isEmpty()) throw new CatalogException("ALLOWED_SOURCE_SYSTEMS_REQUIRED");
    Set<String> allowed = new LinkedHashSet<>();
    for (String sourceSystem : request.allowedSourceSystems()) {
      String normalized = required(sourceSystem, "SOURCE_SYSTEM_REQUIRED");
      if (!normalized.matches("[A-Z][A-Z0-9_]{1,29}")) throw new CatalogException("SOURCE_SYSTEM_INVALID");
      allowed.add(normalized);
    }
    Set<String> requestMappings = new LinkedHashSet<>();
    for (ChannelSourceSystemMapping mapping : request.sourceSystemMappings() == null ? List.<ChannelSourceSystemMapping>of() : request.sourceSystemMappings()) {
      String sourceSystem = required(mapping.sourceSystem(), "SOURCE_SYSTEM_REQUIRED");
      String externalValue = required(mapping.externalValue(), "EXTERNAL_VALUE_REQUIRED");
      if (!allowed.contains(sourceSystem)) throw new CatalogException("SOURCE_SYSTEM_NOT_ALLOWED");
      String key = sourceSystem + "\u0000" + externalValue;
      if (!requestMappings.add(key) || mappingExists.test(sourceSystem, externalValue)) throw new CatalogException("DUPLICATE_SOURCE_MAPPING");
    }
  }

  private static String required(String value, String code) {
    if (value == null || value.isBlank()) throw new CatalogException(code);
    return value;
  }
}
