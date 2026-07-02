package com.wcpe.underwriting.nonqm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class JdbcUnderwritingResultStore implements UnderwritingResultStore {
  private static final String PERSISTENCE_FAILURE = "Durable underwriting result persistence failed";

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;

  JdbcUnderwritingResultStore(DataSource dataSource, ObjectMapper objectMapper) {
    this.dataSource = dataSource;
    this.objectMapper = objectMapper;
  }

  @Override
  public NonQmUnderwritingApi.UnderwritingResult save(NonQmUnderwritingApi.UnderwritingResult result) {
    String payload = serialize(result);
    try (Connection connection = dataSource.getConnection()) {
      if (updateExisting(connection, result, payload) == 0) {
        insertNew(connection, result, payload);
      }
      return result;
    } catch (SQLException ex) {
      throw persistenceUnavailable(ex);
    }
  }

  private int updateExisting(Connection connection, NonQmUnderwritingApi.UnderwritingResult result, String payload)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        update non_qm_underwriting_results set
          underwriting_id = ?,
          product_code = ?,
          product_type = ?,
          decision = ?,
          audit_hash = ?,
          correlation_id = ?,
          result_json = ?,
          updated_at = current_timestamp
        where tenant_id = ? and scenario_id = ?
        """)) {
      statement.setObject(1, result.underwritingId());
      statement.setString(2, result.productCode());
      statement.setString(3, result.productType().name());
      statement.setString(4, result.decision().name());
      statement.setString(5, result.auditHash());
      statement.setString(6, result.correlationId());
      statement.setString(7, payload);
      statement.setString(8, result.tenantId());
      statement.setString(9, result.scenarioId());
      return statement.executeUpdate();
    }
  }

  private void insertNew(Connection connection, NonQmUnderwritingApi.UnderwritingResult result, String payload)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        insert into non_qm_underwriting_results
          (tenant_id, scenario_id, underwriting_id, product_code, product_type, decision, audit_hash, correlation_id, result_json, updated_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
        """)) {
      statement.setString(1, result.tenantId());
      statement.setString(2, result.scenarioId());
      statement.setObject(3, result.underwritingId());
      statement.setString(4, result.productCode());
      statement.setString(5, result.productType().name());
      statement.setString(6, result.decision().name());
      statement.setString(7, result.auditHash());
      statement.setString(8, result.correlationId());
      statement.setString(9, payload);
      statement.executeUpdate();
    }
  }

  @Override
  public Optional<NonQmUnderwritingApi.UnderwritingResult> findByScenarioId(String tenantId, String scenarioId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "select result_json from non_qm_underwriting_results where tenant_id = ? and scenario_id = ? order by updated_at desc limit 1")) {
      statement.setString(1, tenantId);
      statement.setString(2, scenarioId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(deserialize(resultSet.getString("result_json")));
      }
    } catch (SQLException ex) {
      throw persistenceUnavailable(ex);
    }
  }

  private String serialize(NonQmUnderwritingApi.UnderwritingResult result) {
    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to serialize underwriting result", ex);
    }
  }

  private NonQmUnderwritingApi.UnderwritingResult deserialize(String payload) {
    try {
      return objectMapper.readValue(payload, NonQmUnderwritingApi.UnderwritingResult.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to deserialize underwriting result", ex);
    }
  }

  private ResponseStatusException persistenceUnavailable(SQLException ex) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, PERSISTENCE_FAILURE, ex);
  }
}
