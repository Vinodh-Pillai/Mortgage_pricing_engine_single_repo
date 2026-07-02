package com.wcpe.lock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

final class JdbcLockRepositoryTest {
  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final String IDEMPOTENCY_KEY = "FRESH-IDEMP-JDBC-001";
  private static final String RESULT_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Test
  void freshnessIdempotencyLookupUsesTenantAndIdempotencyKeyNotResultHashOnly() {
    CapturingJdbcTemplate jdbc = CapturingJdbcTemplate.empty();
    JdbcLockRepository repository = new JdbcLockRepository(jdbc, new ObjectMapper());

    Optional<LockModels.FreshnessCheckResponse> response = repository.findFreshnessIdempotency(TENANT_ID, IDEMPOTENCY_KEY, RESULT_HASH);

    assertTrue(response.isEmpty());
    assertTrue(jdbc.lastSql.contains("idempotency_key = ?"));
    assertTrue(jdbc.lastSql.contains("tenant_id = ?"));
    assertTrue(!jdbc.lastSql.contains("result_hash = ?"));
    assertArrayEquals(new Object[] { TENANT_ID, IDEMPOTENCY_KEY }, jdbc.lastArgs);
  }

  @Test
  void freshnessIdempotencyLookupConflictsWhenSameKeyHasDifferentPayloadHash() {
    CapturingJdbcTemplate jdbc = CapturingJdbcTemplate.withFreshnessRow("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210");
    JdbcLockRepository repository = new JdbcLockRepository(jdbc, new ObjectMapper());

    LockServiceException conflict = assertThrows(
      LockServiceException.class,
      () -> repository.findFreshnessIdempotency(TENANT_ID, IDEMPOTENCY_KEY, RESULT_HASH)
    );

    assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
    assertArrayEquals(new Object[] { TENANT_ID, IDEMPOTENCY_KEY }, jdbc.lastArgs);
  }

  @Test
  void saveFreshnessCheckPersistsIdempotencyKeySeparatelyFromResultHash() {
    CapturingJdbcTemplate jdbc = CapturingJdbcTemplate.empty();
    JdbcLockRepository repository = new JdbcLockRepository(jdbc, new ObjectMapper());
    LockModels.FreshnessCheckRecord record = freshnessRecord(RESULT_HASH);

    repository.saveFreshnessCheck(record, null, IDEMPOTENCY_KEY, null, null);

    assertTrue(jdbc.lastSql.contains("idempotency_key"));
    assertEquals(RESULT_HASH, jdbc.lastArgs[9]);
    assertEquals(IDEMPOTENCY_KEY, jdbc.lastArgs[10]);
  }

  @Test
  void jdbcRepositoryOverridesEveryDurableRepositorySurface() {
    for (Method method : LockRepository.class.getDeclaredMethods()) {
      if (Modifier.isPrivate(method.getModifiers())) {
        continue;
      }
      assertDoesNotThrow(
        () -> JdbcLockRepository.class.getDeclaredMethod(method.getName(), method.getParameterTypes()),
        () -> "JdbcLockRepository must not inherit fail-closed unavailable() for " + method.getName()
      );
    }
  }

  private static LockModels.FreshnessCheckRecord freshnessRecord(String resultHash) {
    return new LockModels.FreshnessCheckRecord(
      TENANT_ID,
      "FRESHNESS-JDBC-001",
      "QUOTE-FRESHNESS",
      "scenario-hash-v1",
      "freshness-policy-v1",
      LockModels.FreshnessDecisionType.FRESH,
      List.of("QUOTE_FRESH"),
      Instant.parse("2026-06-04T21:00:00Z"),
      Instant.parse("2026-06-04T21:10:00Z"),
      resultHash,
      "loan-officer-7",
      "corr-jdbc"
    );
  }

  private static ResultSet resultSet(String resultHash) {
    return (ResultSet) Proxy.newProxyInstance(
      JdbcLockRepositoryTest.class.getClassLoader(),
      new Class<?>[] { ResultSet.class },
      (proxy, method, args) -> switch (method.getName()) {
        case "getObject" -> TENANT_ID;
        case "getString" -> stringColumn(String.valueOf(args[0]), resultHash);
        case "getTimestamp" -> Timestamp.from(timestampColumn(String.valueOf(args[0])));
        case "wasNull" -> false;
        default -> defaultValue(method.getReturnType());
      }
    );
  }

  private static String stringColumn(String column, String resultHash) {
    return switch (column) {
      case "check_id" -> "FRESHNESS-JDBC-001";
      case "quote_id" -> "QUOTE-FRESHNESS";
      case "scenario_hash" -> "scenario-hash-v1";
      case "policy_version" -> "freshness-policy-v1";
      case "decision" -> "FRESH";
      case "reason_codes" -> "[\"QUOTE_FRESH\"]";
      case "result_hash" -> resultHash;
      case "created_by" -> "loan-officer-7";
      case "correlation_id" -> "corr-jdbc";
      default -> "";
    };
  }

  private static Instant timestampColumn(String column) {
    return switch (column) {
      case "evaluated_at" -> Instant.parse("2026-06-04T21:00:00Z");
      case "expires_at" -> Instant.parse("2026-06-04T21:10:00Z");
      default -> Instant.EPOCH;
    };
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    if (type == double.class) return 0D;
    if (type == char.class) return '\0';
    return null;
  }

  private static final class CapturingJdbcTemplate extends JdbcTemplate {
    private final String rowResultHash;
    private String lastSql;
    private Object[] lastArgs;

    private CapturingJdbcTemplate(String rowResultHash) {
      this.rowResultHash = rowResultHash;
    }

    static CapturingJdbcTemplate empty() {
      return new CapturingJdbcTemplate(null);
    }

    static CapturingJdbcTemplate withFreshnessRow(String resultHash) {
      return new CapturingJdbcTemplate(resultHash);
    }

    @Override
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
      this.lastSql = sql;
      this.lastArgs = args;
      if (rowResultHash == null) {
        throw new EmptyResultDataAccessException(1);
      }
      try {
        return rowMapper.mapRow(resultSet(rowResultHash), 0);
      } catch (Exception ex) {
        throw new AssertionError(ex);
      }
    }

    @Override
    public int update(String sql, Object... args) {
      this.lastSql = sql;
      this.lastArgs = args;
      return 1;
    }
  }
}
