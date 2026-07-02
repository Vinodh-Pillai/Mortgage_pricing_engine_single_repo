package com.wcpe.ratefeed.domain;

import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RateFeedModels.ValidateBatchRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.ValidateBatchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateSheetValidationPolicyTest {
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID BATCH = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID PROFILE = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID INVESTOR = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID CHANNEL = UUID.fromString("40000000-0000-0000-0000-000000000002");
  private static final UUID FORMAT = UUID.fromString("40000000-0000-0000-0000-000000000003");
  private static final UUID ENTRY = UUID.fromString("50000000-0000-0000-0000-000000000001");

  @Mock JdbcTemplate jdbc;
  @Mock RateFeedRepository repository;
  RateFeedService service;

  @BeforeEach
  void setup() {
    RequestContext.roles("RATE_FEED_VALIDATE,RATE_FEED_VIEW");
    service = TestRateFeedServices.create(repository, jdbc);
  }

  @AfterEach
  void cleanup() { RequestContext.clear(); }

  @Test
  void validationProfileIsRequiredToAvoidInventedRules() {
    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.validateBatch(TENANT, BATCH, new ValidateBatchRequest("hash-normalized", null), "idem-1", "worker", "corr-1"));

    assertEquals("VALIDATION_PROFILE_NOT_FOUND", ex.code());
    verifyNoInteractions(repository);
    verifyNoInteractions(jdbc);
  }

  @Test
  void staleNormalizationHashFailsClosed() {
    when(repository.idempotent(any(), eq("idem-2"), any(), eq(ValidateBatchResponse.class), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") Supplier<ValidateBatchResponse> command = inv.getArgument(4);
      return command.get();
    });
    when(repository.batchParseSource(TENANT, BATCH)).thenReturn(new RateFeedRepository.BatchParseSource(BATCH, INVESTOR, CHANNEL, FORMAT, "NORMALIZED", "hash-file", "local://synthetic/src/test/resources/ratesheets/stored-valid.csv"));
    when(jdbc.queryForObject(startsWith("select result_hash from rate_feed.rate_feed_normalization_job"), eq(String.class), any(), any())).thenReturn("latest-hash");

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.validateBatch(TENANT, BATCH, new ValidateBatchRequest("stale-hash", PROFILE), "idem-2", "worker", "corr-2"));

    assertEquals("STALE_NORMALIZATION_HASH", ex.code());
    verify(jdbc, never()).update(startsWith("insert into rate_feed.rate_feed_validation_job"), any(Object[].class));
  }

  @Test
  void duplicateCanonicalEntriesPersistBlockingFindingsAndFailedEvidence() throws Exception {
    when(repository.idempotent(any(), eq("idem-3"), any(), eq(ValidateBatchResponse.class), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") Supplier<ValidateBatchResponse> command = inv.getArgument(4);
      return command.get();
    });
    when(repository.batchParseSource(TENANT, BATCH)).thenReturn(new RateFeedRepository.BatchParseSource(BATCH, INVESTOR, CHANNEL, FORMAT, "NORMALIZED", "hash-file", "local://synthetic/src/test/resources/ratesheets/stored-valid.csv"));
    when(jdbc.queryForObject(startsWith("select result_hash from rate_feed.rate_feed_normalization_job"), eq(String.class), any(), any())).thenReturn("hash-normalized");
    when(jdbc.query(startsWith("select entry_id"), any(RowMapper.class), any(), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") RowMapper<Object> mapper = inv.getArgument(1);
      return List.of(
          mapper.mapRow(entry(ENTRY, 2, "CONV-30", 30, "6.500", "99.125", null, null, null, "INFO"), 0),
          mapper.mapRow(entry(UUID.fromString("50000000-0000-0000-0000-000000000002"), 3, "CONV-30", 30, "6.500", "99.125", null, null, null, "INFO"), 1));
    });
    when(repository.json(any())).thenReturn("{}");
    when(repository.jsonb(any())).thenReturn(null);

    ValidateBatchResponse response = service.validateBatch(TENANT, BATCH, new ValidateBatchRequest("hash-normalized", PROFILE), "idem-3", "worker", "corr-3");

    assertEquals("FAILED", response.status());
    verify(jdbc).update(startsWith("insert into rate_feed.rate_feed_validation_finding"), any(), any(), any(), eq(BATCH), any(), eq("BLOCKER"), eq("DUPLICATE_CANONICAL_ENTRY"), eq(PROFILE.toString()), eq("canonical_product_key"), eq("DUPLICATE_CANONICAL_ENTRY"), any(), eq("REMOVE_DUPLICATE_ENTRY"), eq(3), any());
    verify(repository).outbox(eq(TENANT), eq(BATCH), eq("RateSheetValidationFailed.v1"), eq(1), eq("worker"), eq("corr-3"), any(), any());
    verify(repository).audit(eq(TENANT), eq(BATCH), eq("RATE_SHEET_VALIDATION_FAILED"), eq("RateFeedBatch"), eq("worker"), eq("corr-3"), eq("hash-normalized"), any(), any());
  }

  @Test
  void cleanNormalizedEntriesPassAndEmitValidationPassed() throws Exception {
    when(repository.idempotent(any(), eq("idem-4"), any(), eq(ValidateBatchResponse.class), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") Supplier<ValidateBatchResponse> command = inv.getArgument(4);
      return command.get();
    });
    when(repository.batchParseSource(TENANT, BATCH)).thenReturn(new RateFeedRepository.BatchParseSource(BATCH, INVESTOR, CHANNEL, FORMAT, "NORMALIZED", "hash-file", "local://synthetic/src/test/resources/ratesheets/stored-valid.csv"));
    when(jdbc.queryForObject(startsWith("select result_hash from rate_feed.rate_feed_normalization_job"), eq(String.class), any(), any())).thenReturn("hash-normalized");
    when(jdbc.query(startsWith("select entry_id"), any(RowMapper.class), any(), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") RowMapper<Object> mapper = inv.getArgument(1);
      return List.of(mapper.mapRow(entry(ENTRY, 2, "CONV-30", 30, "6.500", "99.125", null, null, null, "INFO"), 0));
    });
    when(repository.json(any())).thenReturn("{}");

    ValidateBatchResponse response = service.validateBatch(TENANT, BATCH, new ValidateBatchRequest("hash-normalized", PROFILE), "idem-4", "worker", "corr-4");

    assertEquals("PASSED", response.status());
    verify(jdbc, never()).update(startsWith("insert into rate_feed.rate_feed_validation_finding"), any(Object[].class));
    verify(jdbc).update(startsWith("update rate_feed.rate_feed_batch set status"), eq("VALIDATION_PASSED"), any(), eq(TENANT), eq(BATCH));
    verify(repository).outbox(eq(TENANT), eq(BATCH), eq("RateSheetValidationPassed.v1"), eq(1), eq("worker"), eq("corr-4"), any(), any());
  }

  private ResultSet entry(UUID entryId, int sourceRow, String product, int lockPeriod, String rate, String price, String adjustmentType, String adjustmentValue, String adjustmentUnit, String severity) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("entry_id", UUID.class)).thenReturn(entryId);
    when(rs.getInt("source_row_id")).thenReturn(sourceRow);
    when(rs.getString("canonical_product_key")).thenReturn(product);
    when(rs.getInt("lock_period_days")).thenReturn(lockPeriod);
    when(rs.getBigDecimal("rate_percent")).thenReturn(rate == null ? null : new BigDecimal(rate));
    when(rs.getBigDecimal("price_points")).thenReturn(price == null ? null : new BigDecimal(price));
    when(rs.getString("adjustment_type")).thenReturn(adjustmentType);
    when(rs.getBigDecimal("adjustment_value")).thenReturn(adjustmentValue == null ? null : new BigDecimal(adjustmentValue));
    when(rs.getString("adjustment_unit")).thenReturn(adjustmentUnit);
    when(rs.getString("severity")).thenReturn(severity);
    when(rs.getString("message")).thenReturn(null);
    return rs;
  }
}
