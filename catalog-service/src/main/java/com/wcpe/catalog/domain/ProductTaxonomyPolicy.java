package com.wcpe.catalog.domain;

import java.util.Set;
import java.util.function.Predicate;

final class ProductTaxonomyPolicy {
  private static final Set<String> LEVELS = Set.of("FAMILY", "TYPE");
  private static final Set<String> AGENCY_CATEGORIES = Set.of("CONVENTIONAL", "GOVERNMENT", "JUMBO", "NON_QM");

  private ProductTaxonomyPolicy() {}

  static void validateDraft(ProductTaxonomyDraftRequest request, boolean duplicateCode, Predicate<String> activeFamilyExists) {
    if (request.code() == null || !request.code().matches("[A-Z][A-Z0-9_]{1,39}")) throw new CatalogException("PRODUCT_TAXONOMY_CODE_INVALID");
    if (request.name() == null || request.name().length() < 2 || request.name().length() > 100) throw new CatalogException("PRODUCT_TAXONOMY_NAME_INVALID");
    if (!LEVELS.contains(request.level())) throw new CatalogException("PRODUCT_TAXONOMY_LEVEL_INVALID");
    if (!AGENCY_CATEGORIES.contains(request.agencyCategory())) throw new CatalogException("PRODUCT_TAXONOMY_AGENCY_INVALID");
    if (request.displayOrder() == null || request.displayOrder() < 1 || request.displayOrder() > 999) throw new CatalogException("PRODUCT_TAXONOMY_DISPLAY_ORDER_INVALID");
    if (request.effectiveStart() == null) throw new CatalogException("PRODUCT_TAXONOMY_EFFECTIVE_START_REQUIRED");
    if (request.effectiveEnd() != null && !request.effectiveEnd().isAfter(request.effectiveStart())) throw new CatalogException("PRODUCT_TAXONOMY_EFFECTIVE_WINDOW_INVALID");
    if (duplicateCode) throw new CatalogException("PRODUCT_TAXONOMY_CODE_DUPLICATE");
    if ("TYPE".equals(request.level())) {
      if (request.parentCode() == null || request.parentCode().isBlank() || !activeFamilyExists.test(request.parentCode())) throw new CatalogException("INVALID_PARENT_LEVEL");
    }
    if ("FAMILY".equals(request.level()) && request.parentCode() != null && !request.parentCode().isBlank()) throw new CatalogException("INVALID_PARENT_LEVEL");
  }
}
