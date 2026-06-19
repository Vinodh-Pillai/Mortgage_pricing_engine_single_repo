package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class EnumerationCatalogPolicyTest {
  @Test
  void normalizesRawAndCanonicalEnumerationsWithoutInventingVariants() {
    EnumerationCatalogImportRequest request = new EnumerationCatalogImportRequest("ReferenceFormfields.json",
        List.of(new EnumerationTypeInput("AMORTIZATION_TYPE", "Amortization Type", List.of(
            new EnumerationVariantInput("Fixed", "101", "Fixed")))),
        List.of(new EnumerationTypeInput("amortization-type", null, List.of(
            new EnumerationVariantInput("fixed", "101", "Fixed duplicate ignored"),
            new EnumerationVariantInput("ARM", "102", "Adjustable Rate Mortgage")))));

    List<EnumerationTypeResponse> normalized = EnumerationCatalogPolicy.normalize(request);

    assertThat(normalized).hasSize(1);
    EnumerationTypeResponse amortization = normalized.get(0);
    assertThat(amortization.enumTypeId()).isEqualTo("amortization-type");
    assertThat(amortization.name()).isEqualTo("Amortization Type");
    assertThat(amortization.overrideScope()).isEqualTo("system/default");
    assertThat(amortization.variants()).extracting(EnumerationVariantResponse::variantId).containsExactly("fixed", "arm");
    assertThat(amortization.variants()).extracting(EnumerationVariantResponse::oldId).containsExactly("101", "102");
  }

  @Test
  void rejectsEnumerationTypesWithoutVariants() {
    EnumerationCatalogImportRequest request = new EnumerationCatalogImportRequest("ReferenceFormfields.json",
        List.of(new EnumerationTypeInput("loan-purpose", "Loan Purpose", List.of())), List.of());

    assertThatThrownBy(() -> EnumerationCatalogPolicy.normalize(request))
        .isInstanceOf(CatalogException.class)
        .hasMessage("ENUMERATION_VARIANTS_REQUIRED");
  }

  @Test
  void blocksUnsafeVariantDeletionWhenEnumerationIsReferenced() {
    EnumerationTypeResponse existing = new EnumerationTypeResponse("product-channel", "Product Channel", List.of(
        new EnumerationVariantResponse("retail", "101", "Retail"),
        new EnumerationVariantResponse("wholesale", "102", "Wholesale")),
        "ReferenceFormfields.json", "system/default");
    EnumerationCatalogUpdateRequest request = new EnumerationCatalogUpdateRequest("Product Channel", List.of(
        new EnumerationVariantInput("retail", "101", "Retail")), null, "remove wholesale");

    assertThatThrownBy(() -> EnumerationCatalogPolicy.revise(existing, request, true))
        .isInstanceOf(CatalogException.class)
        .hasMessage("ENUM_VARIANT_DELETE_BLOCKED");
  }

  @Test
  void allowsAdditiveTenantOverrideWithoutHardcodedPipelineValues() {
    EnumerationTypeResponse existing = new EnumerationTypeResponse("product-channel", "Product Channel", List.of(
        new EnumerationVariantResponse("retail", "101", "Retail")),
        "ReferenceFormfields.json", "system/default");
    EnumerationCatalogUpdateRequest request = new EnumerationCatalogUpdateRequest("Product Channel", List.of(
        new EnumerationVariantInput("retail", "101", "Retail"),
        new EnumerationVariantInput("wholesale", "102", "Wholesale")), null, "approved ReferenceFormfields tenant override");

    EnumerationTypeResponse revised = EnumerationCatalogPolicy.revise(existing, request, true);

    assertThat(revised.overrideScope()).isEqualTo("tenant");
    assertThat(revised.variants()).extracting(EnumerationVariantResponse::variantId).containsExactly("retail", "wholesale");
  }

  @Test
  void mapsUnknownEnumTypeToNotFoundStatus() {
    assertThat(CatalogController.catalogErrorStatus("ENUM_TYPE_NOT_FOUND"))
        .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
  }
}
