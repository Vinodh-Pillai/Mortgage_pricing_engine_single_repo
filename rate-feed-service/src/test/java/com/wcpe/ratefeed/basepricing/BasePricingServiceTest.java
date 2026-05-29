package com.wcpe.ratefeed.basepricing;

import com.wcpe.ratefeed.basepricing.BasePricingModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BasePricingServiceTest {
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final UUID INVESTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CHANNEL = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SHEET = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final Instant AS_OF = Instant.parse("2026-05-28T10:00:00Z");

  @Test
  void priceReturnsExactSourceRoundingAndReplayDetails() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BasePricingService service = new BasePricingService(jdbc);
    when(jdbc.query(contains("FROM rate_feed.rate_sheet"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(activeSource()));
    when(jdbc.query(contains("WHERE sheet_id=? AND note_rate=? AND lock_period=?"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(cell(new BigDecimal("6.50000"), 30, new BigDecimal("101.126"))));

    BasePricingDecision decision = service.price(request(new BigDecimal("6.50000"), 30));

    assertEquals(DecisionStatus.PRICED, decision.status());
    assertEquals(new BigDecimal("101.13"), decision.basePrice());
    assertEquals(SHEET, decision.sourceDetails().rateSheetId());
    assertEquals(7, decision.sourceDetails().rateSheetVersion());
    assertEquals("grid-v7", decision.sourceDetails().gridVersion());
    assertEquals("exact-v1", decision.sourceDetails().lookupPolicyVersion());
    assertEquals("round-half-up-2", decision.roundingDetails().ruleVersion());
    assertNotNull(decision.replayEvidence().replayHash());
  }

  @Test
  void missingNoteRateCellReturnsStableNoPriceWithoutInterpolation() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BasePricingService service = new BasePricingService(jdbc);
    when(jdbc.query(contains("FROM rate_feed.rate_sheet"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(activeSource()));
    when(jdbc.query(contains("WHERE sheet_id=? AND note_rate=? AND lock_period=?"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    when(jdbc.query(contains("SELECT lock_period"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(30));

    BasePricingDecision decision = service.price(request(new BigDecimal("6.62500"), 30));

    assertEquals(DecisionStatus.NO_PRICE, decision.status());
    assertEquals(NoPriceCode.NO_EXACT_GRID_CELL, decision.noPriceReason().code());
    assertEquals("noteRate", decision.noPriceReason().field());
    assertNull(decision.basePrice());
  }

  @Test
  void missingLockPeriodReturnsUnsupportedLockNoPrice() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BasePricingService service = new BasePricingService(jdbc);
    when(jdbc.query(contains("FROM rate_feed.rate_sheet"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(activeSource()));
    when(jdbc.query(contains("WHERE sheet_id=? AND note_rate=? AND lock_period=?"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    when(jdbc.query(contains("SELECT lock_period"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());

    BasePricingDecision decision = service.price(request(new BigDecimal("6.50000"), 45));

    assertEquals(DecisionStatus.NO_PRICE, decision.status());
    assertEquals(NoPriceCode.UNSUPPORTED_LOCK_PERIOD, decision.noPriceReason().code());
    assertEquals("lockPeriod", decision.noPriceReason().field());
  }

  @Test
  void ambiguousActiveSourcesFailClosed() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BasePricingService service = new BasePricingService(jdbc);
    when(jdbc.query(contains("FROM rate_feed.rate_sheet"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(activeSource(), activeSource()));

    BasePricingDecision decision = service.price(request(new BigDecimal("6.50000"), 30));

    assertEquals(DecisionStatus.NO_PRICE, decision.status());
    assertEquals(NoPriceCode.AMBIGUOUS_ACTIVE_SOURCE, decision.noPriceReason().code());
    verify(jdbc, never()).query(contains("rate_price_point WHERE sheet_id=? AND note_rate=?"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));
  }

  @Test
  void rateStackReturnsOnlyConfiguredEligibleOptionsWhenNoExplicitOptionsAreRequested() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BasePricingService service = new BasePricingService(jdbc);
    when(jdbc.query(contains("FROM rate_feed.rate_sheet"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(activeSource()));
    when(jdbc.query(contains("WHERE sheet_id=? ORDER BY note_rate, lock_period"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(
            cell(new BigDecimal("6.50000"), 30, new BigDecimal("101.126")),
            cell(new BigDecimal("6.75000"), 45, new BigDecimal("100.875"))));

    RateStack stack = service.rateStack(new RateStackRequest(TENANT, INVESTOR, CHANNEL, "CONFORMING_30YR", List.of(), AS_OF,
        "corr-1", "req-1", new LookupPolicy("exact-v1", true), new RoundingPolicy("round-half-up-2", 2, RoundingMode.HALF_UP)));

    assertEquals(2, stack.decisions().size());
    assertTrue(stack.decisions().stream().allMatch(d -> d.status() == DecisionStatus.PRICED));
    assertEquals(List.of(30, 45), stack.decisions().stream().map(BasePricingDecision::lockPeriod).toList());
  }

  @Test
  void rateStackReturnsPricedAndNoPriceEntriesForMixedRequestedOptions() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BasePricingService service = new BasePricingService(jdbc);
    when(jdbc.query(contains("FROM rate_feed.rate_sheet"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(activeSource()));
    when(jdbc.query(contains("WHERE sheet_id=? ORDER BY note_rate, lock_period"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(cell(new BigDecimal("6.50000"), 30, new BigDecimal("101.126"))));
    when(jdbc.query(contains("SELECT lock_period"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());

    RateStack stack = service.rateStack(new RateStackRequest(TENANT, INVESTOR, CHANNEL, "CONFORMING_30YR",
        List.of(new RateStackOption(new BigDecimal("6.50000"), 30), new RateStackOption(new BigDecimal("6.50000"), 45)),
        AS_OF, "corr-1", "req-1", new LookupPolicy("exact-v1", true), new RoundingPolicy("round-half-up-2", 2, RoundingMode.HALF_UP)));

    assertEquals(2, stack.decisions().size());
    assertEquals(DecisionStatus.PRICED, stack.decisions().get(0).status());
    assertEquals(new BigDecimal("101.13"), stack.decisions().get(0).basePrice());
    assertEquals(DecisionStatus.NO_PRICE, stack.decisions().get(1).status());
    assertEquals(NoPriceCode.UNSUPPORTED_LOCK_PERIOD, stack.decisions().get(1).noPriceReason().code());
    assertEquals("lockPeriod", stack.decisions().get(1).noPriceReason().field());
    assertEquals(new BigDecimal("6.50000"), stack.decisions().get(1).noteRate());
    assertEquals(45, stack.decisions().get(1).lockPeriod());
  }

  @Test
  void missingRoundingConfigurationFailsClosed() {
    BasePricingService service = new BasePricingService(mock(JdbcTemplate.class));
    BasePricingRequest request = new BasePricingRequest(TENANT, INVESTOR, CHANNEL, "CONFORMING_30YR",
        new BigDecimal("6.50000"), 30, AS_OF, "corr-1", "req-1", new LookupPolicy("exact-v1", true), null);

    BasePricingDecision decision = service.price(request);

    assertEquals(DecisionStatus.NO_PRICE, decision.status());
    assertEquals(NoPriceCode.MISSING_ROUNDING_CONFIGURATION, decision.noPriceReason().code());
  }

  private BasePricingRequest request(BigDecimal noteRate, int lockPeriod) {
    return new BasePricingRequest(TENANT, INVESTOR, CHANNEL, "CONFORMING_30YR", noteRate, lockPeriod, AS_OF,
        "corr-1", "req-1", new LookupPolicy("exact-v1", true), new RoundingPolicy("round-half-up-2", 2, RoundingMode.HALF_UP));
  }

  private BasePricingService.ActiveSource activeSource() {
    return new BasePricingService.ActiveSource(SHEET, 7, "grid-v7", "result-v7", AS_OF.minusSeconds(60));
  }

  private BasePricingService.GridCell cell(BigDecimal noteRate, int lockPeriod, BigDecimal basePrice) {
    return new BasePricingService.GridCell(noteRate, lockPeriod, basePrice, 1);
  }
}
