package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CatalogContractServiceTest {
  private final CatalogContractService service = new CatalogContractService();

  @Test
  void returnsDeterministicFeatureDisabledContractWithoutRuntimeDependencies() {
    CatalogContractResponse first = service.lookup(new CatalogContractRequest("CONV30", "FNMA", "tenant-a", "RETAIL", LocalDate.parse("2026-01-01"), false));
    CatalogContractResponse second = service.lookup(new CatalogContractRequest("CONV30", "FNMA", "tenant-a", "RETAIL", LocalDate.parse("2026-01-01"), false));

    assertThat(first).isEqualTo(second);
    assertThat(first.status()).isEqualTo("FEATURE_DISABLED");
    assertThat(first.contractCapabilities()).containsExactly("catalog-contract-skeleton");
  }

  @Test
  void rejectsInvalidProductAndInvestorIdentifiersExplicitly() {
    assertThatThrownBy(() -> service.lookup(new CatalogContractRequest(" ", "FNMA", null, null, null, false)))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_PRODUCT_IDENTIFIER");
    assertThatThrownBy(() -> service.lookup(new CatalogContractRequest("CONV30", "bad investor", null, null, null, false)))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_INVESTOR_IDENTIFIER");
  }
}
