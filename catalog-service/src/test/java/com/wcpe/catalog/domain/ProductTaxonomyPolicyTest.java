package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductTaxonomyPolicyTest {
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void requiresParentForType() {
    ProductTaxonomyDraftRequest request = new ProductTaxonomyDraftRequest("CONVENTIONAL_FIXED", "Conventional Fixed Rate", "TYPE", null, "CONVENTIONAL", START, null, 20);

    assertThatThrownBy(() -> ProductTaxonomyPolicy.validateDraft(request, false, code -> false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_PARENT_LEVEL");
  }

  @Test
  void acceptsConventionalFamilyAndTypeWithActiveFamilyParent() {
    ProductTaxonomyDraftRequest family = new ProductTaxonomyDraftRequest("CONVENTIONAL", "Conventional", "FAMILY", null, "CONVENTIONAL", START, null, 10);
    ProductTaxonomyDraftRequest type = new ProductTaxonomyDraftRequest("CONVENTIONAL_ARM", "Conventional Adjustable Rate", "TYPE", "CONVENTIONAL", "CONVENTIONAL", START, null, 30);

    assertThatCode(() -> ProductTaxonomyPolicy.validateDraft(family, false, code -> false)).doesNotThrowAnyException();
    assertThatCode(() -> ProductTaxonomyPolicy.validateDraft(type, false, code -> "CONVENTIONAL".equals(code))).doesNotThrowAnyException();
  }

  @Test
  void validatesCodeWindowAndDisplayOrder() {
    assertThatThrownBy(() -> ProductTaxonomyPolicy.validateDraft(new ProductTaxonomyDraftRequest("bad", "Bad", "FAMILY", null, "CONVENTIONAL", START, null, 1), false, code -> false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_TAXONOMY_CODE_INVALID");
    assertThatThrownBy(() -> ProductTaxonomyPolicy.validateDraft(new ProductTaxonomyDraftRequest("CONVENTIONAL", "Conventional", "FAMILY", null, "CONVENTIONAL", START, START, 1), false, code -> false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_TAXONOMY_EFFECTIVE_WINDOW_INVALID");
    assertThatThrownBy(() -> ProductTaxonomyPolicy.validateDraft(new ProductTaxonomyDraftRequest("CONVENTIONAL", "Conventional", "FAMILY", null, "CONVENTIONAL", START, null, 1000), false, code -> false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_TAXONOMY_DISPLAY_ORDER_INVALID");
  }
}
