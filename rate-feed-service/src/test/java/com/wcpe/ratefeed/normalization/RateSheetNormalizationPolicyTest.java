package com.wcpe.ratefeed.domain;

import com.wcpe.ratefeed.domain.RateFeedModels.NormalizeBatchRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.NormalizeBatchResponse;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateSheetNormalizationPolicyTest {
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID BATCH = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID PROFILE = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID INVESTOR = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID CHANNEL = UUID.fromString("40000000-0000-0000-0000-000000000002");
  private static final UUID FORMAT = UUID.fromString("40000000-0000-0000-0000-000000000003");

  @Mock JdbcTemplate jdbc;
  @Mock RateFeedRepository repository;
  RateFeedService service;

  @BeforeEach
  void setup() {
    RequestContext.roles("RATE_FEED_NORMALIZE,RATE_FEED_VIEW");
    service = TestRateFeedServices.create(repository, jdbc);
  }

  @AfterEach
  void cleanup() { RequestContext.clear(); }

  @Test
  void normalizationProfileIsRequiredToAvoidInventedMappings() {
    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.normalizeBatch(TENANT, BATCH, new NormalizeBatchRequest(null, null), "idem-1", "worker", "corr-1"));

    assertEquals("NORMALIZATION_PROFILE_NOT_FOUND", ex.code());
    verifyNoInteractions(repository);
    verifyNoInteractions(jdbc);
  }

  @Test
  void normalizesCanonicalParsedRowsAndEmitsEvidence() throws Exception {
    when(repository.idempotent(any(), eq("idem-2"), any(), eq(NormalizeBatchResponse.class), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") Supplier<NormalizeBatchResponse> command = inv.getArgument(4);
      return command.get();
    });
    when(repository.batchParseSource(TENANT, BATCH)).thenReturn(new RateFeedRepository.BatchParseSource(BATCH, INVESTOR, CHANNEL, FORMAT, "PARSED", "hash-file"));
    when(jdbc.queryForObject(startsWith("select result_hash"), eq(String.class), any(), any())).thenReturn("hash-parse");
    when(jdbc.query(startsWith("select source_row_number"), any(RowMapper.class), any(), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") RowMapper<Object> mapper = inv.getArgument(1);
      return List.of(
          mapper.mapRow(field(2, "canonical_product_key", "CONV-30", "CONV-30", "INFO"), 0),
          mapper.mapRow(field(2, "note_rate", "6.500", "6.500", "INFO"), 1),
          mapper.mapRow(field(2, "base_price", "99.125", "99.125", "INFO"), 2),
          mapper.mapRow(field(2, "lock_period", "30", "30", "INFO"), 3));
    });
    when(repository.json(any())).thenReturn("{}");
    when(repository.jsonb(any())).thenReturn(null);

    NormalizeBatchResponse response = service.normalizeBatch(TENANT, BATCH, new NormalizeBatchRequest("hash-parse", PROFILE), "idem-2", "worker", "corr-2");

    assertEquals("NORMALIZED", response.status());
    verify(jdbc).update(startsWith("insert into rate_feed.normalized_rate_sheet_entry"), any(), any(), eq(BATCH), eq(2), eq(INVESTOR), eq(CHANNEL), eq("CONV-30"), isNull(), eq(30), any(), any(), isNull(), isNull(), isNull(), any(), any(), any(), any(), eq("INFO"), eq("normalized"), any());
    verify(repository).outbox(eq(TENANT), eq(BATCH), eq("RateSheetNormalized.v1"), eq(1), eq("worker"), eq("corr-2"), any(), any());
    verify(repository).audit(eq(TENANT), eq(BATCH), eq("RATE_SHEET_NORMALIZED"), eq("RateFeedBatch"), eq("worker"), eq("corr-2"), eq("hash-parse"), any(), any());
  }

  @Test
  void unknownCanonicalProductMappingFailsClosed() throws Exception {
    when(repository.idempotent(any(), eq("idem-3"), any(), eq(NormalizeBatchResponse.class), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") Supplier<NormalizeBatchResponse> command = inv.getArgument(4);
      return command.get();
    });
    when(repository.batchParseSource(TENANT, BATCH)).thenReturn(new RateFeedRepository.BatchParseSource(BATCH, INVESTOR, CHANNEL, FORMAT, "PARSED", "hash-file"));
    when(jdbc.queryForObject(startsWith("select result_hash"), eq(String.class), any(), any())).thenReturn("hash-parse");
    when(jdbc.query(startsWith("select source_row_number"), any(RowMapper.class), any(), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") RowMapper<Object> mapper = inv.getArgument(1);
      return List.of(
          mapper.mapRow(field(2, "note_rate", "6.500", "6.500", "INFO"), 0),
          mapper.mapRow(field(2, "base_price", "99.125", "99.125", "INFO"), 1),
          mapper.mapRow(field(2, "lock_period", "30", "30", "INFO"), 2));
    });
    when(repository.json(any())).thenReturn("{}");

    NormalizeBatchResponse response = service.normalizeBatch(TENANT, BATCH, new NormalizeBatchRequest("hash-parse", PROFILE), "idem-3", "worker", "corr-3");

    assertEquals("NORMALIZATION_FAILED", response.status());
    verify(jdbc, never()).update(startsWith("insert into rate_feed.normalized_rate_sheet_entry"), any(Object[].class));
    verify(repository).outbox(eq(TENANT), eq(BATCH), eq("RateSheetNormalizationFailed.v1"), eq(1), eq("worker"), eq("corr-3"), any(), any());
  }

  @Test
  void rawValuesWithoutGovernedCandidatesFailClosed() throws Exception {
    when(repository.idempotent(any(), eq("idem-4"), any(), eq(NormalizeBatchResponse.class), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") Supplier<NormalizeBatchResponse> command = inv.getArgument(4);
      return command.get();
    });
    when(repository.batchParseSource(TENANT, BATCH)).thenReturn(new RateFeedRepository.BatchParseSource(BATCH, INVESTOR, CHANNEL, FORMAT, "PARSED", "hash-file"));
    when(jdbc.queryForObject(startsWith("select result_hash"), eq(String.class), any(), any())).thenReturn("hash-parse");
    when(jdbc.query(startsWith("select source_row_number"), any(RowMapper.class), any(), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") RowMapper<Object> mapper = inv.getArgument(1);
      return List.of(
          mapper.mapRow(field(2, "canonical_product_key", "CONV-30", null, "INFO"), 0),
          mapper.mapRow(field(2, "note_rate", "6.500", "6.500", "INFO"), 1),
          mapper.mapRow(field(2, "base_price", "99.125", "99.125", "INFO"), 2),
          mapper.mapRow(field(2, "lock_period", "30", "30", "INFO"), 3));
    });
    when(repository.json(any())).thenReturn("{}");

    NormalizeBatchResponse response = service.normalizeBatch(TENANT, BATCH, new NormalizeBatchRequest("hash-parse", PROFILE), "idem-4", "worker", "corr-4");

    assertEquals("NORMALIZATION_FAILED", response.status());
    verify(jdbc, never()).update(startsWith("insert into rate_feed.normalized_rate_sheet_entry"), any(Object[].class));
  }

  @Test
  void mappingRefsIncludeProfileAndFieldCandidates() throws Exception {
    when(repository.idempotent(any(), eq("idem-5"), any(), eq(NormalizeBatchResponse.class), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") Supplier<NormalizeBatchResponse> command = inv.getArgument(4);
      return command.get();
    });
    when(repository.batchParseSource(TENANT, BATCH)).thenReturn(new RateFeedRepository.BatchParseSource(BATCH, INVESTOR, CHANNEL, FORMAT, "PARSED", "hash-file"));
    when(jdbc.queryForObject(startsWith("select result_hash"), eq(String.class), any(), any())).thenReturn("hash-parse");
    when(jdbc.query(startsWith("select source_row_number"), any(RowMapper.class), any(), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked") RowMapper<Object> mapper = inv.getArgument(1);
      return List.of(
          mapper.mapRow(field(7, "canonical_product_key", "Conv 30", "CONV-30", "INFO"), 0),
          mapper.mapRow(field(7, "note_rate", "6.5%", "6.500000", "INFO"), 1),
          mapper.mapRow(field(7, "base_price", "99-04", "99.12500", "INFO"), 2),
          mapper.mapRow(field(7, "lock_period", "thirty", "30", "INFO"), 3),
          mapper.mapRow(field(7, "adjustment_type", "fico", "CREDIT_SCORE", "INFO"), 4),
          mapper.mapRow(field(7, "adjustment_value", "25 bps", "25", "INFO"), 5),
          mapper.mapRow(field(7, "adjustment_unit", "bps", "BPS_ADJUSTMENT", "INFO"), 6));
    });
    when(repository.json(any())).thenReturn("{}");
    when(repository.jsonb(any())).thenReturn(null);

    NormalizeBatchResponse response = service.normalizeBatch(TENANT, BATCH, new NormalizeBatchRequest("hash-parse", PROFILE), "idem-5", "worker", "corr-5");

    assertEquals("NORMALIZED", response.status());
    verify(repository, atLeastOnce()).jsonb(argThat(value -> value instanceof java.util.Map<?, ?> refs
        && PROFILE.toString().equals(refs.get("profileVersion"))
        && refs.containsKey("fields")));
    verify(jdbc).update(startsWith("insert into rate_feed.normalized_rate_sheet_entry"), any(), any(), eq(BATCH), eq(7), eq(INVESTOR), eq(CHANNEL), eq("CONV-30"), isNull(), eq(30), any(), any(), eq("CREDIT_SCORE"), any(), eq("BPS_ADJUSTMENT"), any(), any(), any(), any(), eq("INFO"), eq("normalized"), any());
  }

  private ResultSet field(int row, String name, String raw, String candidate, String severity) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getInt("source_row_number")).thenReturn(row);
    when(rs.getString("field_name")).thenReturn(name);
    when(rs.getString("raw_value")).thenReturn(raw);
    when(rs.getString("candidate_value")).thenReturn(candidate);
    when(rs.getInt("source_column")).thenReturn(1);
    when(rs.getString("severity")).thenReturn(severity);
    when(rs.getString("error_code")).thenReturn(null);
    when(rs.getString("message")).thenReturn(null);
    return rs;
  }
}
