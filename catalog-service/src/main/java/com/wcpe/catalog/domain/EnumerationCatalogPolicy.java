package com.wcpe.catalog.domain;

import java.util.*;

final class EnumerationCatalogPolicy {
  private EnumerationCatalogPolicy() {}

  static List<EnumerationTypeResponse> normalize(EnumerationCatalogImportRequest request) {
    if (request == null) throw new CatalogException("ENUMERATION_IMPORT_REQUEST_REQUIRED");
    Map<String, EnumerationAccumulator> byType = new LinkedHashMap<>();
    merge(byType, request.rawEnumerations());
    merge(byType, request.enumerations());
    if (byType.isEmpty()) throw new CatalogException("ENUMERATION_TYPES_REQUIRED");
    String source = blank(request.sourceName()) ? "LoanPass field library" : request.sourceName().trim();
    return byType.values().stream().map(acc -> acc.toResponse(source)).toList();
  }

  static EnumerationTypeResponse revise(EnumerationTypeResponse existing, EnumerationCatalogUpdateRequest request, boolean referencedByFields) {
    if (existing == null) throw new CatalogException("ENUM_TYPE_NOT_FOUND");
    if (request == null) throw new CatalogException("ENUMERATION_UPDATE_REQUEST_REQUIRED");
    List<EnumerationVariantInput> requested = request.variants() == null ? List.of() : request.variants();
    if (requested.isEmpty()) throw new CatalogException("ENUMERATION_VARIANTS_REQUIRED");
    EnumerationAccumulator acc = new EnumerationAccumulator(existing.enumTypeId(), label(request.name(), existing.name()));
    for (EnumerationVariantInput variant : requested) {
      String variantId = normalizeId(variant == null ? null : variant.variantId(), "ENUM_VARIANT_ID_REQUIRED");
      String variantLabel = required(variant == null ? null : variant.label(), "ENUM_VARIANT_LABEL_REQUIRED");
      String oldId = trimToNull(variant.oldId());
      acc.addVariant(new EnumerationVariantResponse(variantId, oldId, variantLabel));
    }
    Set<String> existingIds = new LinkedHashSet<>();
    for (EnumerationVariantResponse variant : existing.variants() == null ? List.<EnumerationVariantResponse>of() : existing.variants()) {
      existingIds.add(normalizeId(variant.variantId(), "ENUM_VARIANT_ID_REQUIRED"));
    }
    Set<String> revisedIds = new LinkedHashSet<>(acc.variants.keySet());
    existingIds.removeAll(revisedIds);
    if (referencedByFields && !existingIds.isEmpty()) throw new CatalogException("ENUM_VARIANT_DELETE_BLOCKED");
    return new EnumerationTypeResponse(existing.enumTypeId(), acc.name, List.copyOf(acc.variants.values()), existing.source(), "tenant");
  }

  private static void merge(Map<String, EnumerationAccumulator> byType, List<EnumerationTypeInput> inputs) {
    for (EnumerationTypeInput input : inputs == null ? List.<EnumerationTypeInput>of() : inputs) {
      String enumTypeId = normalizeId(input == null ? null : input.enumTypeId(), "ENUM_TYPE_ID_REQUIRED");
      String name = label(input.name(), enumTypeId);
      EnumerationAccumulator acc = byType.computeIfAbsent(enumTypeId, id -> new EnumerationAccumulator(id, name));
      acc.mergeName(name);
      List<EnumerationVariantInput> variants = input.variants() == null ? List.of() : input.variants();
      if (variants.isEmpty()) throw new CatalogException("ENUMERATION_VARIANTS_REQUIRED");
      for (EnumerationVariantInput variant : variants) {
        String variantId = normalizeId(variant == null ? null : variant.variantId(), "ENUM_VARIANT_ID_REQUIRED");
        String variantLabel = required(variant == null ? null : variant.label(), "ENUM_VARIANT_LABEL_REQUIRED");
        String oldId = trimToNull(variant.oldId());
        acc.addVariant(new EnumerationVariantResponse(variantId, oldId, variantLabel));
      }
    }
  }

  private static String normalizeId(String value, String errorCode) {
    return required(value, errorCode).toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static String label(String value, String fallback) {
    return blank(value) ? fallback : value.trim();
  }

  private static String required(String value, String errorCode) {
    if (blank(value)) throw new CatalogException(errorCode);
    return value.trim();
  }

  private static String trimToNull(String value) {
    return blank(value) ? null : value.trim();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static final class EnumerationAccumulator {
    private final String enumTypeId;
    private String name;
    private final Map<String, EnumerationVariantResponse> variants = new LinkedHashMap<>();

    private EnumerationAccumulator(String enumTypeId, String name) {
      this.enumTypeId = enumTypeId;
      this.name = name;
    }

    private void mergeName(String candidate) {
      if (name.equals(enumTypeId) && !candidate.equals(enumTypeId)) name = candidate;
    }

    private void addVariant(EnumerationVariantResponse variant) {
      variants.putIfAbsent(variant.variantId(), variant);
    }

    private EnumerationTypeResponse toResponse(String source) {
      return new EnumerationTypeResponse(enumTypeId, name, List.copyOf(variants.values()), source, "system/default");
    }
  }
}
