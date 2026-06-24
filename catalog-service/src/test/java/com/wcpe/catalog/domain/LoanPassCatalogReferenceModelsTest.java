package com.wcpe.catalog.domain;

import com.wcpe.catalog.domain.LoanPassCatalogReferenceModels.LoanPassProductCatalogRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanPassCatalogReferenceModelsTest {
  @Test
  void requiresExplicitSyntheticDevProvenanceForSyntheticSources() {
    assertThatThrownBy(() -> new LoanPassProductCatalogRef(UUID.randomUUID(), "PROD-A", "LP-PROD-A", null, null,
        "SYNTHETIC_DEV", "dev generator fixture", null, false, Map.of(), true, null, null))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("SYNTHETIC_DEV_PROVENANCE_REQUIRED");
  }

  @Test
  void preservesExternalRefsWithoutEmbeddingPricingRules() {
    LoanPassProductCatalogRef ref = new LoanPassProductCatalogRef(UUID.randomUUID(), "PROD-A", "LP-PROD-A", "external-type",
        "external-investor", "LOANPASS_PUBLIC", "public swagger/captured shape reference", "evidence/ref.json", false,
        Map.of("shapeOnly", true), true, Instant.parse("2026-01-01T00:00:00Z"), null);

    assertThat(ref.loanPassProductRef()).isEqualTo("LP-PROD-A");
    assertThat(ref.metadata()).containsEntry("shapeOnly", true);
  }
}
