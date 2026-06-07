package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class ModelVersionGovernanceIT {
  @Test
  void shouldInvalidateControlsAndRuntimeOnSuspension() {
    ModelRegistryService service = ModelVersionGovernanceFixtures.registryWithVisibleApproval();
    String modelVersionId = service.list(AdvisoryTestFixtures.TENANT, ModelStatus.APPROVED_ADVISORY_VISIBLE, AdvisoryType.PRICING).get(0).modelVersionId();

    ModelVersionResponse response =
        service
            .suspend(new SuspendModelVersionCommand(AdvisoryTestFixtures.TENANT, modelVersionId, "risk-admin-1", "MRM-SUSPEND-1", "drift review", "corr-suspend"))
            .value()
            .orElseThrow();

    assertEquals(ModelStatus.SUSPENDED, response.status());
    assertFalse(response.cacheInvalidationRef().isBlank());
    assertFalse(service.discoverable(AdvisoryTestFixtures.TENANT, modelVersionId, "ml-advisory-feature-schema-v1"));
  }

  @Test
  void shouldPersistGovernanceStateThroughJdbcRepositoryAcrossServiceInstances() throws Exception {
    DataSource dataSource = migratedPostgresCompatibleDataSource();
    Clock clock = Clock.fixed(AdvisoryTestFixtures.NOW, ZoneOffset.UTC);
    ModelRegistryService firstService = new ModelRegistryService(clock, new JdbcModelVersionRepository(dataSource));
    RegisterModelVersionCommand command = ModelVersionGovernanceFixtures.registerCommand("sha256:persistent", AllowedUse.ADVISORY_ONLY, true);

    ModelVersionResponse registered = firstService.register(command).value().orElseThrow();
    firstService.submitReview(AdvisoryTestFixtures.TENANT, registered.modelVersionId(), "ml-operator-2", "corr-submit-review");
    firstService.approve(ModelVersionGovernanceFixtures.approveVisible(registered.modelVersionId()));

    ModelRegistryService restartedService = new ModelRegistryService(clock, new JdbcModelVersionRepository(dataSource));
    MlAdvisoryResult<ModelVersionResponse> replayed = restartedService.register(command);
    RegisterModelVersionCommand conflictingReplay =
        new RegisterModelVersionCommand(
            command.tenantId(),
            command.idempotencyKey(),
            command.actorId(),
            command.modelName(),
            command.semanticVersion(),
            command.advisoryTypes(),
            command.allowedUse(),
            command.artifactUri(),
            "sha256:conflicting-payload",
            command.featureSchemaVersion(),
            command.owner(),
            command.evidence(),
            command.lineageRefs(),
            command.correlationId());
    MlAdvisoryResult<ModelVersionResponse> conflict = restartedService.register(conflictingReplay);

    assertTrue(replayed.valid());
    assertEquals(registered.modelVersionId(), replayed.value().orElseThrow().modelVersionId());
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.errorCode().orElseThrow());
    assertEquals(3, countRows(dataSource, "ml_model_status_history"));
    assertEquals(3, restartedService.outboxEvents().size());
    assertEquals(3, restartedService.auditRecords().size());
    assertEquals(1, restartedService.list(AdvisoryTestFixtures.TENANT, ModelStatus.APPROVED_ADVISORY_VISIBLE, AdvisoryType.PRICING).size());
    assertEquals(1, countRows(dataSource, "ml_model_idempotency"));
    assertEquals(5, countRows(dataSource, "ml_model_governance_evidence"));
  }

  @Test
  void shouldExposeJdbcDataSourceConstructorForRuntimePersistenceWiring() throws NoSuchMethodException {
    assertNotNull(ModelVersionGovernanceController.class.getConstructor(DataSource.class));
  }

  private DataSource migratedPostgresCompatibleDataSource() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:pii14s09_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      for (String sql : modelVersionGovernanceMigration().split(";")) {
        if (!sql.isBlank()) {
          statement.execute(sql);
        }
      }
    }
    return dataSource;
  }

  private String modelVersionGovernanceMigration() throws IOException {
    try (InputStream inputStream = getClass().getResourceAsStream("/db/migration/V7__model_version_governance.sql")) {
      assertNotNull(inputStream, "V7 model version governance migration must be on the test classpath");
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
          .replace("timestamptz", "timestamp with time zone");
    }
  }

  private int countRows(DataSource dataSource, String tableName) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select count(*) from " + tableName)) {
      assertTrue(resultSet.next());
      return resultSet.getInt(1);
    }
  }
}
