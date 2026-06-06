package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;

class MarketCatalogImportTest {
  @Test
  void rejectsInvalidCountyFips() {
    assertThatThrownBy(() -> MarketCatalogPolicy.normalizeCountyFips("TX", "06001"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_COUNTY_FIPS");
  }

  @Test
  void acceptsUsStatesAndDc() {
    assertThat(MarketCatalogPolicy.requireStateCode("tx")).isEqualTo("TX");
    assertThat(MarketCatalogPolicy.requireStateCode("DC")).isEqualTo("DC");
  }

  @Test
  void marketCatalogChangedPayloadIncludesContractFieldsAndNoAddressPii() {
    UUID tenantId = UUID.randomUUID();
    UUID marketVersionId = UUID.randomUUID();
    MarketArea market = new MarketArea(UUID.randomUUID(), "TX", "Texas", "48201", "Harris", "RESTRICTED", "PRODUCT_CHANNEL_ALLOWED", List.of("RETAIL"), List.of("CONV_FIXED_30"), LocalDate.of(2026, 1, 1), null);

    Map<String, Object> payload = MarketCatalogPolicy.changedPayload(tenantId, marketVersionId, market, 1, "sha256:market-config");

    assertThat(payload)
        .containsEntry("eventKey", tenantId + ":TX:48201")
        .containsEntry("marketVersionId", marketVersionId.toString())
        .containsEntry("versionNumber", 1)
        .containsEntry("stateCode", "TX")
        .containsEntry("countyFips", "48201")
        .containsEntry("status", "RESTRICTED")
        .containsEntry("restrictionReasonCode", "PRODUCT_CHANNEL_ALLOWED")
        .containsEntry("configHash", "sha256:market-config");
    Object effectiveWindow = payload.get("effectiveWindow");
    assertThat(effectiveWindow).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) effectiveWindow).get("start")).isEqualTo("2026-01-01T00:00:00Z");
    assertThat(payload).doesNotContainKeys("streetAddress", "borrowerName", "borrowerSsn");
  }

  @Test
  void duplicateImportSourceHashMapsToRequiredConflictStatus() {
    assertThat(CatalogController.catalogErrorStatus("IMPORT_ALREADY_PROCESSED")).isEqualTo(HttpStatus.CONFLICT);
  }
}
