package com.wcpe.ratefeed.basepricing;

import com.wcpe.ratefeed.basepricing.BasePricingModels.*;
import com.wcpe.ratefeed.domain.Hashing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BasePricingService {
  private static final String CONTRACT_VERSION = "base-pricing.v1";

  private final JdbcTemplate jdbc;

  public BasePricingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public BasePricingDecision price(BasePricingRequest request) {
    NoPriceReason validation = validateRequest(request, true);
    if (validation != null) return noPrice(request, null, null, validation);

    List<ActiveSource> sources = activeSources(request.tenantId(), request.investorId(), request.channelId(),
        request.productCode(), request.asOf());
    if (sources.isEmpty()) {
      return noPrice(request, null, null, reason(NoPriceCode.NO_ACTIVE_SOURCE, "No accepted active rate sheet for request identity and as-of timestamp", false, "asOf"));
    }
    if (sources.size() > 1) {
      return noPrice(request, null, null, reason(NoPriceCode.AMBIGUOUS_ACTIVE_SOURCE, "Multiple accepted active rate sheets matched request identity and as-of timestamp", false, "asOf"));
    }

    ActiveSource source = sources.get(0);
    return priceCell(request, source, request.noteRate(), request.lockPeriod());
  }

  public RateStack rateStack(RateStackRequest request) {
    NoPriceReason validation = validateStackRequest(request);
    if (validation != null) {
      BasePricingRequest noPriceRequest = new BasePricingRequest(request.tenantId(), request.investorId(), request.channelId(),
          request.productCode(), null, null, request.asOf(), request.correlationId(), request.requestId(),
          request.lookupPolicy(), request.roundingPolicy());
      return new RateStack(List.of(noPrice(noPriceRequest, null, null, validation)));
    }

    List<ActiveSource> sources = activeSources(request.tenantId(), request.investorId(), request.channelId(),
        request.productCode(), request.asOf());
    BasePricingRequest baseRequest = new BasePricingRequest(request.tenantId(), request.investorId(), request.channelId(),
        request.productCode(), null, null, request.asOf(), request.correlationId(), request.requestId(),
        request.lookupPolicy(), request.roundingPolicy());
    if (sources.isEmpty()) {
      return new RateStack(List.of(noPrice(baseRequest, null, null, reason(NoPriceCode.NO_ACTIVE_SOURCE, "No accepted active rate sheet for request identity and as-of timestamp", false, "asOf"))));
    }
    if (sources.size() > 1) {
      return new RateStack(List.of(noPrice(baseRequest, null, null, reason(NoPriceCode.AMBIGUOUS_ACTIVE_SOURCE, "Multiple accepted active rate sheets matched request identity and as-of timestamp", false, "asOf"))));
    }

    ActiveSource source = sources.get(0);
    List<GridCell> cells = configuredCells(source.sheetId());
    Map<String, GridCell> cellsByCoordinate = new LinkedHashMap<>();
    for (GridCell cell : cells) {
      cellsByCoordinate.put(coordinateKey(cell.noteRate(), cell.lockPeriod()), cell);
    }
    List<BasePricingDecision> decisions = new ArrayList<>();
    if (request.requestedOptions() == null || request.requestedOptions().isEmpty()) {
      for (GridCell cell : cells) {
        decisions.add(pricedStackDecision(request, source, cell));
      }
      return new RateStack(decisions);
    }

    for (RateStackOption option : request.requestedOptions()) {
      BasePricingRequest itemRequest = new BasePricingRequest(request.tenantId(), request.investorId(), request.channelId(),
          request.productCode(), option.noteRate(), option.lockPeriod(), request.asOf(), request.correlationId(),
          request.requestId(), request.lookupPolicy(), request.roundingPolicy());
      GridCell cell = cellsByCoordinate.get(coordinateKey(option.noteRate(), option.lockPeriod()));
      if (cell == null) {
        String field = option.lockPeriod() == null || !hasLockPeriod(source.sheetId(), option.lockPeriod()) ? "lockPeriod" : "noteRate";
        NoPriceCode code = field.equals("lockPeriod") ? NoPriceCode.UNSUPPORTED_LOCK_PERIOD : NoPriceCode.NO_EXACT_GRID_CELL;
        decisions.add(noPrice(itemRequest, source, null, reason(code, "Requested rate-stack option is not configured as an exact grid cell", false, field)));
      } else {
        decisions.add(pricedDecision(itemRequest, source, cell));
      }
    }
    return new RateStack(decisions);
  }

  private BasePricingDecision pricedStackDecision(RateStackRequest request, ActiveSource source, GridCell cell) {
    BasePricingRequest itemRequest = new BasePricingRequest(request.tenantId(), request.investorId(), request.channelId(),
        request.productCode(), cell.noteRate(), cell.lockPeriod(), request.asOf(), request.correlationId(),
        request.requestId(), request.lookupPolicy(), request.roundingPolicy());
    return pricedDecision(itemRequest, source, cell);
  }

  private String coordinateKey(BigDecimal noteRate, Integer lockPeriod) {
    return (noteRate == null ? "" : noteRate.stripTrailingZeros().toPlainString()) + "|" + lockPeriod;
  }

  private BasePricingDecision priceCell(BasePricingRequest request, ActiveSource source, BigDecimal noteRate, int lockPeriod) {
    List<GridCell> cells = jdbc.query(
        "SELECT note_rate, lock_period, base_price, grid_position FROM rate_feed.rate_price_point WHERE sheet_id=? AND note_rate=? AND lock_period=?",
        (rs, row) -> new GridCell(rs.getBigDecimal("note_rate"), rs.getInt("lock_period"),
            rs.getBigDecimal("base_price"), rs.getInt("grid_position")),
        source.sheetId(), noteRate, lockPeriod);
    if (cells.isEmpty()) {
      String field = hasLockPeriod(source.sheetId(), lockPeriod) ? "noteRate" : "lockPeriod";
      NoPriceCode code = field.equals("lockPeriod") ? NoPriceCode.UNSUPPORTED_LOCK_PERIOD : NoPriceCode.NO_EXACT_GRID_CELL;
      return noPrice(request, source, null, reason(code, "No configured exact grid cell for note rate and lock period", false, field));
    }
    return pricedDecision(request, source, cells.get(0));
  }

  private BasePricingDecision pricedDecision(BasePricingRequest request, ActiveSource source, GridCell cell) {
    BigDecimal rounded = cell.basePrice().setScale(request.roundingPolicy().scale(), request.roundingPolicy().mode());
    BasePricingSourceDetails sourceDetails = sourceDetails(request, source, cell);
    RoundingDetails roundingDetails = new RoundingDetails(request.roundingPolicy().ruleVersion(), cell.basePrice(), rounded);
    ReplayEvidence replayEvidence = replayEvidence(request, source, cell, rounded);
    return new BasePricingDecision(DecisionStatus.PRICED, rounded, cell.noteRate(), cell.lockPeriod(), sourceDetails,
        roundingDetails, null, replayEvidence, request.correlationId(), request.requestId(), Instant.now(), CONTRACT_VERSION);
  }

  private List<ActiveSource> activeSources(UUID tenantId, UUID investorId, UUID channelId, String productCode, Instant asOf) {
    return jdbc.query(
        "SELECT sheet_id, version, grid_hash, result_hash, activated_at FROM rate_feed.rate_sheet " +
            "WHERE tenant_id=? AND investor_id=? AND channel_id=? AND product_code=? AND status='ACTIVE' " +
            "AND effective_at <= ?::timestamptz AND (effective_until IS NULL OR effective_until > ?::timestamptz) " +
            "ORDER BY version DESC LIMIT 2",
        (rs, row) -> new ActiveSource(rs.getObject("sheet_id", UUID.class), rs.getInt("version"),
            rs.getString("grid_hash"), rs.getString("result_hash"), toInstant(rs.getTimestamp("activated_at"))),
        tenantId, investorId, channelId, productCode, java.sql.Timestamp.from(asOf), java.sql.Timestamp.from(asOf));
  }

  private List<GridCell> configuredCells(UUID sheetId) {
    return jdbc.query(
        "SELECT note_rate, lock_period, base_price, grid_position FROM rate_feed.rate_price_point WHERE sheet_id=? ORDER BY note_rate, lock_period",
        (rs, row) -> new GridCell(rs.getBigDecimal("note_rate"), rs.getInt("lock_period"),
            rs.getBigDecimal("base_price"), rs.getInt("grid_position")),
        sheetId);
  }

  private boolean hasLockPeriod(UUID sheetId, int lockPeriod) {
    List<Integer> matches = jdbc.query("SELECT lock_period FROM rate_feed.rate_price_point WHERE sheet_id=? AND lock_period=? LIMIT 1",
        (rs, row) -> rs.getInt("lock_period"), sheetId, lockPeriod);
    return !matches.isEmpty();
  }

  private NoPriceReason validateRequest(BasePricingRequest request, boolean requireCell) {
    if (request == null || request.tenantId() == null || request.investorId() == null || request.channelId() == null ||
        request.productCode() == null || request.productCode().isBlank() || request.asOf() == null ||
        request.correlationId() == null || request.requestId() == null ||
        (requireCell && (request.noteRate() == null || request.lockPeriod() == null))) {
      return reason(NoPriceCode.MISSING_REQUIRED_IDENTITY, "Required base-pricing request identity or grid coordinate is missing", false, "request");
    }
    if (request.lookupPolicy() == null || request.lookupPolicy().policyVersion() == null || !request.lookupPolicy().exactOnly()) {
      return reason(NoPriceCode.MISSING_LOOKUP_POLICY, "Exact-only lookup policy version is required", false, "lookupPolicy");
    }
    if (request.roundingPolicy() == null || request.roundingPolicy().ruleVersion() == null || request.roundingPolicy().mode() == null) {
      return reason(NoPriceCode.MISSING_ROUNDING_CONFIGURATION, "Configured deterministic rounding rule is required", false, "roundingPolicy");
    }
    return null;
  }

  private NoPriceReason validateStackRequest(RateStackRequest request) {
    if (request == null) return reason(NoPriceCode.MISSING_REQUIRED_IDENTITY, "Rate stack request is missing", false, "request");
    BasePricingRequest base = new BasePricingRequest(request.tenantId(), request.investorId(), request.channelId(),
        request.productCode(), BigDecimal.ONE, 1, request.asOf(), request.correlationId(), request.requestId(),
        request.lookupPolicy(), request.roundingPolicy());
    return validateRequest(base, false);
  }

  private BasePricingSourceDetails sourceDetails(BasePricingRequest request, ActiveSource source, GridCell cell) {
    String gridCellId = source.sheetId() + ":" + cell.noteRate().toPlainString() + ":" + cell.lockPeriod();
    return new BasePricingSourceDetails(source.sheetId(), source.version(), source.gridHash(), gridCellId,
        cell.noteRate(), cell.lockPeriod(), source.activatedAt(), request.asOf(), request.lookupPolicy().policyVersion(),
        request.roundingPolicy().ruleVersion(), source.resultHash());
  }

  private ReplayEvidence replayEvidence(BasePricingRequest request, ActiveSource source, GridCell cell, BigDecimal rounded) {
    List<String> inputs = List.of(
        request.tenantId().toString(), request.investorId().toString(), request.channelId().toString(),
        request.productCode(), request.asOf().toString(), source.sheetId().toString(), Integer.toString(source.version()),
        source.gridHash(), cell.noteRate().toPlainString(), Integer.toString(cell.lockPeriod()),
        request.lookupPolicy().policyVersion(), request.roundingPolicy().ruleVersion(), rounded.toPlainString());
    return new ReplayEvidence(Hashing.sha256(String.join("|", inputs)), inputs);
  }

  private BasePricingDecision noPrice(BasePricingRequest request, ActiveSource source, GridCell cell, NoPriceReason reason) {
    BasePricingSourceDetails details = source == null ? null : sourceDetails(request, source,
        cell == null ? new GridCell(request.noteRate(), request.lockPeriod(), null, -1) : cell);
    ReplayEvidence replay = new ReplayEvidence(Hashing.sha256(reason.code() + ":" + reason.field() + ":" + reason.message()),
        List.of(reason.code().name(), reason.field() == null ? "" : reason.field()));
    return new BasePricingDecision(DecisionStatus.NO_PRICE, null, request == null ? null : request.noteRate(),
        request == null ? null : request.lockPeriod(), details, null, reason, replay,
        request == null ? null : request.correlationId(), request == null ? null : request.requestId(), Instant.now(), CONTRACT_VERSION);
  }

  private NoPriceReason reason(NoPriceCode code, String message, boolean retryable, String field) {
    return new NoPriceReason(code, message, retryable, field, Hashing.sha256(code + ":" + field + ":" + message));
  }

  private Instant toInstant(java.sql.Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

  public record ActiveSource(UUID sheetId, int version, String gridHash, String resultHash, Instant activatedAt) {}
  public record GridCell(BigDecimal noteRate, Integer lockPeriod, BigDecimal basePrice, int gridPosition) {}
}
