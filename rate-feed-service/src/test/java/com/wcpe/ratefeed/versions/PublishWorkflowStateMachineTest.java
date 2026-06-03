package com.wcpe.ratefeed.domain;

import com.wcpe.ratefeed.domain.RateFeedModels.ApprovalDecisionRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.PublishRateSheetRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.PublishWorkflowDecision;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishWorkflowStateMachineTest {
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID VERSION = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final String GRID_HASH = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String VERSION_HASH = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

  @Mock
  JdbcTemplate jdbc;

  RateFeedService service;

  @BeforeEach
  void setup() {
    RequestContext.roles("RATE_FEED_WRITER,RATE_FEED_APPROVER,RATE_FEED_ACTIVATE");
    service = TestRateFeedServices.create(new RateFeedRepository(jdbc, new com.fasterxml.jackson.databind.ObjectMapper()), jdbc);
    lenient().when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(), any()))
        .thenThrow(new EmptyResultDataAccessException(1));
  }

  @AfterEach
  void cleanup() {
    RequestContext.clear();
  }

  @Test
  void approvalDecisionBlocksSubmitterBySeparationOfDuties() throws Exception {
    mockSheet("PENDING_APPROVAL");
    when(jdbc.queryForObject(eq("select submitted_by from rate_feed.rate_sheet where tenant_id=? and sheet_id=?"), eq(String.class), eq(TENANT), eq(VERSION)))
        .thenReturn("submitter");

    RateFeedException ex = assertThrows(RateFeedException.class, () -> service.decideApproval(TENANT, VERSION,
        new ApprovalDecisionRequest(PublishWorkflowDecision.APPROVE, "APPROVED_BY_POLICY", "reviewed"), "idem-approve", "submitter", "corr-1"));

    assertEquals("SOD_VIOLATION", ex.code());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void publishRequiresCurrentValidationAndVersionHashes() throws Exception {
    mockSheet("APPROVED");
    when(jdbc.queryForObject(eq("select submitted_by from rate_feed.rate_sheet where tenant_id=? and sheet_id=?"), eq(String.class), eq(TENANT), eq(VERSION)))
        .thenReturn("submitter");

    RateFeedException ex = assertThrows(RateFeedException.class, () -> service.publishVersion(TENANT, VERSION,
        new PublishRateSheetRequest(Instant.parse("2026-01-01T00:00:00Z"), "sha256:stale", VERSION_HASH), "idem-publish", "publisher", "corr-1"));

    assertEquals("STALE_VERSION_HASH", ex.code());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  private void mockSheet(String status) throws Exception {
    when(jdbc.queryForObject(eq("select * from rate_feed.rate_sheet where tenant_id=? and sheet_id=? for update"), any(Object[].class), any(RowMapper.class)))
        .thenAnswer(inv -> {
          RowMapper<RateFeedModels.RateSheet> mapper = inv.getArgument(2);
          return mapper.mapRow(sheetResultSet(status), 0);
        });
  }

  private ResultSet sheetResultSet(String status) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("sheet_id", UUID.class)).thenReturn(VERSION);
    when(rs.getObject("tenant_id", UUID.class)).thenReturn(TENANT);
    when(rs.getObject("investor_id", UUID.class)).thenReturn(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    when(rs.getObject("channel_id", UUID.class)).thenReturn(UUID.fromString("10000000-0000-0000-0000-000000000002"));
    when(rs.getString("product_code")).thenReturn("CONV-30");
    when(rs.getInt("version")).thenReturn(2);
    when(rs.getString("status")).thenReturn(status);
    when(rs.getTimestamp("effective_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    when(rs.getTimestamp("effective_until")).thenReturn(null);
    when(rs.getString("file_sha256")).thenReturn("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
    when(rs.getString("grid_hash")).thenReturn(GRID_HASH);
    when(rs.getString("grid_points")).thenReturn("{}");
    when(rs.getInt("row_count")).thenReturn(1);
    when(rs.getString("result_hash")).thenReturn(VERSION_HASH);
    when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    when(rs.getString("created_by")).thenReturn("submitter");
    when(rs.getTimestamp("activated_at")).thenReturn(null);
    when(rs.getString("activated_by")).thenReturn(null);
    when(rs.getTimestamp("rejected_at")).thenReturn(null);
    when(rs.getString("rejected_by")).thenReturn(null);
    when(rs.getString("rejection_reason")).thenReturn(null);
    when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    return rs;
  }
}
