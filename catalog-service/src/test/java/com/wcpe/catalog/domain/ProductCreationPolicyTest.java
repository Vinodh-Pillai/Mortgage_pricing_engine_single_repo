package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProductCreationPolicyTest {
  @Test
  void validProductCreationStoresSuppliedLoanPassMappingRefsAsMetadata() {
    ProductCreationDraft draft = ProductCreationPolicy.validate(validRequest(Map.of("loanPassProductCode", "LP-CONV30")));

    assertThat(draft.productCode()).isEqualTo("CONV30");
    assertThat(draft.status()).isEqualTo("DRAFT");
    assertThat(draft.metadataRefs()).containsEntry("loanPassProductCode", "LP-CONV30");
    assertThat(draft.supportedTerms()).containsExactly(360);
  }

  @Test
  void missingRequiredMetadataFailsClosedBeforePersistence() {
    ProductCreationRequest missingName = new ProductCreationRequest("CONV30", " ", "CONVENTIONAL", "FIXED",
        List.of(360), List.of("FIXED"), List.of("PURCHASE"), List.of("RETAIL"), List.of("TX"), Map.of(), Instant.parse("2026-01-01T00:00:00Z"), null, "DRAFT");

    assertThatThrownBy(() -> ProductCreationPolicy.validate(missingName))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_NAME_REQUIRED");
  }

  @Test
  void invalidEffectiveWindowRejectedWithActionableCode() {
    ProductCreationRequest invalid = new ProductCreationRequest("CONV30", "Conventional 30", "CONVENTIONAL", "FIXED",
        List.of(360), List.of("FIXED"), List.of("PURCHASE"), List.of("RETAIL"), List.of("TX"), Map.of(), Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), "DRAFT");

    assertThatThrownBy(() -> ProductCreationPolicy.validate(invalid))
        .isInstanceOf(CatalogException.class)
        .hasMessage("EFFECTIVE_WINDOW_INVALID");
  }

  @Test
  void invalidMappingMetadataRejectedBeforeMutation() {
    ProductCreationRequest invalid = validRequest(Collections.singletonMap(" ", "LP-CONV30"));

    assertThatThrownBy(() -> ProductCreationPolicy.validate(invalid))
        .isInstanceOf(CatalogException.class)
        .hasMessage("MAPPING_METADATA_KEY_REQUIRED");
  }

  private static ProductCreationRequest validRequest(Map<String, Object> metadataRefs) {
    return new ProductCreationRequest("conv30", "Conventional 30", "conventional", "fixed",
        List.of(360), List.of("fixed"), List.of("purchase"), List.of("retail"), List.of("tx"), metadataRefs,
        Instant.parse("2026-01-01T00:00:00Z"), null, null);
  }
}
