package com.wcpe.catalog.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoanPassCatalogReferenceMigrationTest {
  @Test
  void migrationCreatesDurableCatalogRefsWithSourceProvenance() throws IOException {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/V16__loanpass_product_catalog_refs.sql"));

    assertThat(sql).contains("catalog.loanpass_product_catalog_ref");
    assertThat(sql).contains("catalog.loanpass_product_availability_ref");
    assertThat(sql).contains("source_provenance");
    assertThat(sql).contains("synthetic_dev_only");
    assertThat(sql.toUpperCase()).doesNotContain("INSERT INTO");
  }
}
