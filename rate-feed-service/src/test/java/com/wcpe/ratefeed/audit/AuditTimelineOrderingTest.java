package com.wcpe.ratefeed.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.domain.RateFeedModels;
import com.wcpe.ratefeed.role.RateFeedRoles;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditTimelineOrderingTest {

  @Test
  void timeline_orders_by_occurred_at_ascending() {
    CapturedTimeline captured = runTimelineQuery(request(null, null, null, null, 0, 50));

    assertTrue(captured.sql().contains("order by e.occurred_at asc"),
        "SQL should contain order by occurred_at asc: " + captured.sql());
  }

  @Test
  void timeline_orders_by_sequence_number_when_same_time() {
    CapturedTimeline captured = runTimelineQuery(request(null, null, null, null, 0, 50));

    assertTrue(captured.sql().contains("e.sequence_number asc"),
        "SQL should contain sequence_number asc: " + captured.sql());
    assertTrue(captured.sql().indexOf("e.occurred_at asc") < captured.sql().indexOf("e.sequence_number asc"),
        "occurred_at should come before sequence_number in: " + captured.sql());
  }

  @Test
  void pagination_correct_page_and_size() {
    CapturedTimeline captured = runTimelineQuery(request(null, null, null, null, 2, 25));

    Object[] params = captured.params();
    assertEquals(25, params[params.length - 2]);
    assertEquals(50, params[params.length - 1]);
  }

  @Test
  void filters_apply_correctly() {
    Instant from = Instant.parse("2026-06-01T00:00:00Z");
    Instant to = Instant.parse("2026-06-02T00:00:00Z");
    UUID batchId = UUID.randomUUID();
    CapturedTimeline captured = runTimelineQuery(request(from, to, batchId, "RATE_SHEET_ACTIVATED", 1, 10));

    assertTrue(captured.sql().contains("e.occurred_at >= ?"),
        "SQL should contain occurred_at >= ?: " + captured.sql());
    assertTrue(captured.sql().contains("e.occurred_at <= ?"),
        "SQL should contain occurred_at <= ?: " + captured.sql());
    assertTrue(captured.sql().contains("e.aggregate_id=?"),
        "SQL should contain aggregate_id=?: " + captured.sql());
    assertTrue(captured.sql().contains("e.event_type=?"),
        "SQL should contain event_type=?: " + captured.sql());

    List<Object> params = List.of(captured.params());
    assertTrue(params.contains(Timestamp.from(from)),
        () -> "params should contain from timestamp: " + params);
    assertTrue(params.contains(Timestamp.from(to)),
        () -> "params should contain to timestamp: " + params);
    assertEquals(2, params.stream().filter(batchId::equals).count(),
        () -> "batchId should appear twice in params: " + params);
    assertTrue(params.contains("RATE_SHEET_ACTIVATED"),
        () -> "params should contain event type: " + params);
    assertTrue(params.contains(10),
        () -> "params should contain size 10: " + params);
    assertTrue(params.contains(10),
        () -> "params should contain offset 10: " + params);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private CapturedTimeline runTimelineQuery(RateFeedModels.AuditTimelineRequest request) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AtomicReference<String> sql = new AtomicReference<>();
    AtomicReference<Object[]> params = new AtomicReference<>();

    when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
    when(jdbc.query(anyString(), any(RowMapper.class), any()))
        .thenAnswer(invocation -> {
          sql.set(invocation.getArgument(0));
          params.set((Object[]) invocation.getArgument(2));
          return List.of();
        });

    AuditReportService service = new AuditReportService(jdbc, new ObjectMapper(), 2555);
    RateFeedModels.AuditTimelinePage page = service.queryTimeline(UUID.randomUUID(), request,
        Set.of(RateFeedRoles.RATE_FEED_AUDIT_VIEW));

    assertNotNull(page);
    assertEquals(request.page(), page.page());
    assertEquals(request.size(), page.size());
    assertTrue(sql.get() != null, "SQL was not captured - mock may not have matched");
    assertTrue(params.get() != null, "Params were not captured - mock may not have matched");
    return new CapturedTimeline(sql.get(), params.get());
  }

  private RateFeedModels.AuditTimelineRequest request(Instant from, Instant to, UUID batchId,
      String eventType, int page, int size) {
    return new RateFeedModels.AuditTimelineRequest(from, to, null, null, batchId, null, null,
        eventType, null, null, page, size);
  }

  private record CapturedTimeline(String sql, Object[] params) {}
}
