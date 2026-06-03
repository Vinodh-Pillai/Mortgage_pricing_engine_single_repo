package com.wcpe.ratefeed.domain;

import com.wcpe.ratefeed.domain.RateFeedModels.CreateRateSheetVersionRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RateFeedModels.RateSheetVersionResolveResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateSheetVersioningPolicyTest {
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID INVESTOR = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID CHANNEL = UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final Instant AS_OF = Instant.parse("2026-01-15T00:00:00Z");

  @Mock
  JdbcTemplate jdbc;

  RateFeedService service;

  @BeforeEach
  void setup() {
    RequestContext.roles("RATE_FEED_VIEW,RATE_FEED_WRITER");
    service = TestRateFeedServices.create(new RateFeedRepository(jdbc, new com.fasterxml.jackson.databind.ObjectMapper()), jdbc);
  }

  @AfterEach
  void cleanup() {
    RequestContext.clear();
  }

  @Test
  void createVersionRequiresLineageReason() {
    CreateRateSheetVersionRequest request = new CreateRateSheetVersionRequest(
        UUID.randomUUID(), "CONV-30", "January sheet", AS_OF, null, " ", null, null);

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.createRateSheetVersion(TENANT, request, "idem-1", "admin", "corr-1"));

    assertEquals("VERSION_LINEAGE_REQUIRED", ex.code());
    verifyNoInteractions(jdbc);
  }

  @Test
  void resolveFailsClosedWhenNoActiveCandidateMatches() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.resolveRateSheetVersion(TENANT, INVESTOR, CHANNEL, "CONV-30", AS_OF));

    assertEquals("VERSION_NOT_FOUND", ex.code());
  }

  @Test
  void resolveFailsClosedWhenMultipleActiveCandidatesMatch() throws Exception {
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any()))
        .thenAnswer(inv -> {
          RowMapper<RateSheetVersionResolveResponse> mapper = inv.getArgument(1);
          return List.of(mapper.mapRow(resultSet(UUID.randomUUID(), 1), 0), mapper.mapRow(resultSet(UUID.randomUUID(), 2), 1));
        });

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.resolveRateSheetVersion(TENANT, INVESTOR, CHANNEL, "CONV-30", AS_OF));

    assertEquals("AMBIGUOUS_ACTIVE_VERSION", ex.code());
  }

  @Test
  void resolveReturnsSingleActiveCandidate() throws Exception {
    UUID versionId = UUID.randomUUID();
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any()))
        .thenAnswer(inv -> {
          RowMapper<RateSheetVersionResolveResponse> mapper = inv.getArgument(1);
          return List.of(mapper.mapRow(resultSet(versionId, 7), 0));
        });

    RateSheetVersionResolveResponse response = service.resolveRateSheetVersion(TENANT, INVESTOR, CHANNEL, "CONV-30", AS_OF);

    assertEquals(versionId, response.rateSheetVersionId());
    assertEquals(7, response.versionNumber());
    assertEquals("ACTIVE", response.status());
  }

  private ResultSet resultSet(UUID versionId, int version) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("sheet_id", UUID.class)).thenReturn(versionId);
    when(rs.getInt("version")).thenReturn(version);
    when(rs.getObject("investor_id", UUID.class)).thenReturn(INVESTOR);
    when(rs.getObject("channel_id", UUID.class)).thenReturn(CHANNEL);
    when(rs.getString("product_code")).thenReturn("CONV-30");
    when(rs.getTimestamp("effective_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    when(rs.getTimestamp("effective_until")).thenReturn(null);
    when(rs.getString("status")).thenReturn("ACTIVE");
    when(rs.getString("result_hash")).thenReturn("sha256:test");
    return rs;
  }
}
