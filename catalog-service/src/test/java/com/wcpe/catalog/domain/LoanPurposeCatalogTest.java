package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class LoanPurposeCatalogTest {
  @Test
  void cashOutRequiresRefinance() {
    assertThatThrownBy(() -> LoanPurposeCatalogPolicy.validateDraft(new LoanPurposeDraftRequest(
        "CASH_OUT_REFI", "Cash-Out Refinance", "REFINANCE", false, true, true, true, List.of("CASH_OUT_REFINANCE"), Instant.parse("2026-01-01T00:00:00Z"), null), false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("CASH_OUT_REQUIRES_REFINANCE");
  }

  @Test
  void purchaseCannotRequireExistingLien() {
    assertThatThrownBy(() -> LoanPurposeCatalogPolicy.validateDraft(new LoanPurposeDraftRequest(
        "PURCHASE", "Purchase", "PURCHASE", false, false, true, true, List.of("PUR"), Instant.parse("2026-01-01T00:00:00Z"), null), false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PURCHASE_CANNOT_REQUIRE_EXISTING_LIEN");
  }

  @Test
  void acceptsStoryCitedLoanPurposeSeeds() {
    LoanPurposeCatalogPolicy.validateDraft(new LoanPurposeDraftRequest("PURCHASE", "Purchase", "PURCHASE", false, false, false, true, List.of("PUR"), Instant.parse("2026-01-01T00:00:00Z"), null), false);
    LoanPurposeCatalogPolicy.validateDraft(new LoanPurposeDraftRequest("RATE_TERM_REFI", "Rate/Term Refinance", "REFINANCE", true, false, true, true, List.of("RATE_TERM_REFINANCE"), Instant.parse("2026-01-01T00:00:00Z"), null), false);
    LoanPurposeCatalogPolicy.validateDraft(new LoanPurposeDraftRequest("CASH_OUT_REFI", "Cash-Out Refinance", "REFINANCE", true, true, true, true, List.of("CASH_OUT_REFINANCE"), Instant.parse("2026-01-01T00:00:00Z"), null), false);
    LoanPurposeCatalogPolicy.validateDraft(new LoanPurposeDraftRequest("CONSTRUCTION_TO_PERMANENT", "Construction-to-Permanent", "CONSTRUCTION", true, false, true, false, List.of("CONSTRUCTION_PERM"), Instant.parse("2026-01-01T00:00:00Z"), null), false);
  }

  @Test
  void draftApiDeclaresCreatedStatus() throws Exception {
    Method method = CatalogController.class.getDeclaredMethod("addLoanPurpose", UUID.class, LoanPurposeDraftRequest.class, HttpServletRequest.class);

    ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

    assertThat(responseStatus).isNotNull();
    assertThat(responseStatus.value()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void mvpSeedMigrationProvidesExecutableSeedData() throws Exception {
    String seedSql = Files.readString(Path.of("src/main/resources/db/migration/V10__loan_purpose_catalog_seed_data.sql"));

    assertThat(seedSql).contains("create or replace function catalog.seed_mvp_loan_purposes");
    assertThat(seedSql).contains("PURCHASE", "RATE_TERM_REFI", "CASH_OUT_REFI", "CONSTRUCTION_TO_PERMANENT");
    assertThat(seedSql).contains("eligible_for_conventional, aliases");
  }
}
