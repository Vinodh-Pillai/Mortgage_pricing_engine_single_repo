package com.wcpe.underwriting.nonqm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.EligibilityOutcome;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.EligibilityStatus;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.NonQmProductType;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.PricingContext;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.PricingStatus;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.UnderwritingRequest;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.UnderwritingResult;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class JdbcUnderwritingResultStoreTest {
  @Test
  void persistsAndReloadsResultByScenarioAcrossStoreInstances() throws Exception {
    DataSource dataSource = migratedPostgresCompatibleDataSource();
    UnderwritingResult expected = new NonQmUnderwritingApi().evaluate(request());

    new JdbcUnderwritingResultStore(dataSource, new ObjectMapper()).save(expected);
    UnderwritingResult reloaded = new JdbcUnderwritingResultStore(dataSource, new ObjectMapper())
        .findByScenarioId(expected.tenantId(), expected.scenarioId())
        .orElseThrow();

    assertThat(reloaded).isEqualTo(expected);
  }

  @Test
  void upsertsLatestResultForScenario() throws Exception {
    DataSource dataSource = migratedPostgresCompatibleDataSource();
    JdbcUnderwritingResultStore store = new JdbcUnderwritingResultStore(dataSource, new ObjectMapper());
    UnderwritingResult first = new NonQmUnderwritingApi().evaluate(request());
    UnderwritingResult second = new NonQmUnderwritingApi().evaluate(request("corr-2"));

    store.save(first);
    store.save(second);

    assertThat(store.findByScenarioId(first.tenantId(), first.scenarioId()).orElseThrow().correlationId()).isEqualTo("corr-2");
  }

  @Test
  void doesNotReturnSameScenarioAcrossTenantBoundary() throws Exception {
    DataSource dataSource = migratedPostgresCompatibleDataSource();
    JdbcUnderwritingResultStore store = new JdbcUnderwritingResultStore(dataSource, new ObjectMapper());

    store.save(new NonQmUnderwritingApi().evaluate(request("tenant-1", "scenario-DSCR", "corr-tenant-1")));
    store.save(new NonQmUnderwritingApi().evaluate(request("tenant-2", "scenario-DSCR", "corr-tenant-2")));

    assertThat(store.findByScenarioId("tenant-1", "scenario-DSCR").orElseThrow().correlationId())
        .isEqualTo("corr-tenant-1");
    assertThat(store.findByScenarioId("tenant-3", "scenario-DSCR")).isEmpty();
  }

  private static DataSource migratedPostgresCompatibleDataSource() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:underwriting_" + UUID.randomUUID()
        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute(migrationSql());
    }
    return dataSource;
  }

  private static String migrationSql() throws Exception {
    try (InputStream inputStream = JdbcUnderwritingResultStoreTest.class.getResourceAsStream(
        "/db/migration/V1__non_qm_underwriting_results.sql")) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static UnderwritingRequest request() {
    return request("corr-1");
  }

  private static UnderwritingRequest request(String correlationId) {
    return request("tenant-1", "scenario-DSCR", correlationId);
  }

  private static UnderwritingRequest request(String tenantId, String scenarioId, String correlationId) {
    return new UnderwritingRequest(tenantId, scenarioId, NonQmProductType.DSCR,
        "NONQM-DSCR", "INV-A", "BROKER", Instant.parse("2026-06-13T00:00:00Z"), Map.of(
        "nonQm.dscr.ratio", "1.18",
        "income.rental.evidenceRef", "doc:rental:1",
        "property.taxInsurance.evidenceRef", "doc:pitia:1",
        "credit.fico", "742",
        "credit.tradelines", "3",
        "credit.housingHistory", "0x30",
        "property.appraisalRef", "appraisal:1",
        "property.condition", "C3",
        "property.type", "SFR"),
        new EligibilityOutcome(EligibilityStatus.ELIGIBLE, "eligibility:nonqm:passed", "NON_QM_ELIGIBLE", List.of()),
        pricedContext(), Map.of(), correlationId);
  }

  private static PricingContext pricedContext() {
    return new PricingContext(PricingStatus.PRICED, "pricing-hash-1", "nonqm-rate-sheet", 1,
        "INV-PROD-1", List.of(), List.of("nonqm-rate-sheet:v1", "nonqm-margin:v1"), Map.of("ltvBand", "70_75"));
  }
}
