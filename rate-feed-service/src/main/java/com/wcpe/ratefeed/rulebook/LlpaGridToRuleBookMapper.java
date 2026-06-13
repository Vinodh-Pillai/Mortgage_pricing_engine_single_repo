package com.wcpe.ratefeed.rulebook;

import com.wcpe.ratefeed.parser.CsvParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps GSE LLPA CSV grids into an adjustment rule-book draft without hard-coded LLPA values. */
@Component
public final class LlpaGridToRuleBookMapper {
  private static final List<String> CORE_COLUMNS = List.of("FICO_BAND", "LTV_BAND", "LOAN_PURPOSE", "PROPERTY_TYPE", "OCCUPANCY", "UNITS");
  private static final Set<String> OPTIONAL_COLUMNS = Set.of("STATE", "COUNTY", "VERSION");
  private static final DateTimeFormatter BUSINESS_KEY_MONTH = DateTimeFormatter.ofPattern("yyyy_MM").withZone(ZoneOffset.UTC);

  public AdjustmentRuleBookDraft mapToRuleBook(String csvContent, MappingConfig config) {
    MappingConfig normalizedConfig = MappingConfig.normalize(config);
    ParsedGrid parsed = parse(csvContent);
    Map<String, LlpaGridRow> latestByCell = new LinkedHashMap<>();
    List<MappingWarning> warnings = new ArrayList<>(parsed.warnings());

    int sourceRow = 0;
    for (Map<String, String> row : parsed.rows()) {
      sourceRow++;
      try {
        LlpaGridRow llpa = LlpaGridRow.from(row, sourceRow);
        latestByCell.merge(llpa.cellKey(), llpa, (oldRow, newRow) -> newRow.version() >= oldRow.version() ? newRow : oldRow);
      } catch (IllegalArgumentException ex) {
        warnings.add(new MappingWarning(sourceRow, "ROW_SKIPPED", ex.getMessage()));
      }
    }

    List<AdjustmentRuleDraft> rules = latestByCell.values().stream()
        .map(row -> toRule(row, normalizedConfig))
        .toList();

    String businessKey = normalizedConfig.investor().toUpperCase(Locale.ROOT) + "_LLPA_" + BUSINESS_KEY_MONTH.format(normalizedConfig.effectiveAt());
    String gridHash = sha256(csvContent == null ? "" : csvContent);
    return new AdjustmentRuleBookDraft(
        normalizedConfig.tenantId(),
        UUID.nameUUIDFromBytes((normalizedConfig.tenantId() + businessKey + gridHash).getBytes(StandardCharsets.UTF_8)),
        businessKey,
        normalizedConfig.versionLabel(),
        "DRAFT",
        new RuleBookSelectorDraft(normalizedConfig.productFamily(), normalizedConfig.investor(), normalizedConfig.channel()),
        new EffectiveWindowDraft(normalizedConfig.effectiveAt(), null),
        new PrecisionPolicyDraft(3, 1, 2, "HALF_UP"),
        rules,
        gridHash,
        parsed.rows().size(),
        warnings,
        Map.of("rateSheetId", Optional.ofNullable(normalizedConfig.rateSheetId()).map(UUID::toString).orElse(""), "source", normalizedConfig.sourceSystem()));
  }

  private ParsedGrid parse(String csvContent) {
    if (csvContent == null || csvContent.isBlank()) {
      throw new IllegalArgumentException("LLPA CSV content is required for rule-book mapping");
    }
    List<String> dataLines = csvContent.lines()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .filter(line -> !line.startsWith("#"))
        .toList();
    if (dataLines.size() < 2) throw new IllegalArgumentException("LLPA CSV must include a header and at least one row");
    char delimiter = CsvParser.detectDelimiter(dataLines);
    String[] headers = CsvParser.tokenizeLine(dataLines.get(0), delimiter);
    Map<String, Integer> headerIndex = new LinkedHashMap<>();
    for (int i = 0; i < headers.length; i++) headerIndex.put(normalizeHeader(headers[i]), i);
    for (String required : CORE_COLUMNS) {
      if (!headerIndex.containsKey(required)) throw new IllegalArgumentException("LLPA CSV missing required column " + required);
    }
    if (!headerIndex.containsKey("LLPA_BPS")) throw new IllegalArgumentException("LLPA CSV missing required column LLPA_BPS");

    List<Map<String, String>> rows = new ArrayList<>();
    List<MappingWarning> warnings = new ArrayList<>();
    for (int i = 1; i < dataLines.size(); i++) {
      String[] tokens = CsvParser.tokenizeLine(dataLines.get(i), delimiter);
      Map<String, String> row = new LinkedHashMap<>();
      for (Map.Entry<String, Integer> entry : headerIndex.entrySet()) {
        row.put(entry.getKey(), entry.getValue() < tokens.length ? tokens[entry.getValue()].trim() : "");
      }
      for (String optional : OPTIONAL_COLUMNS) row.putIfAbsent(optional, "");
      if (row.getOrDefault("STATE", "").isBlank()) {
        row.put("STATE", "ALL");
        warnings.add(new MappingWarning(i + 1, "DEFAULTED_STATE", "STATE defaulted to ALL"));
      }
      rows.add(row);
    }
    return new ParsedGrid(rows, warnings);
  }

  private AdjustmentRuleDraft toRule(LlpaGridRow row, MappingConfig config) {
    List<AdjustmentConditionDraft> conditions = new ArrayList<>();
    conditions.add(condition("ficoBandKey", row.ficoBand()));
    conditions.add(condition("ltvBandKey", row.ltvBand()));
    conditions.add(condition("loanPurpose", row.loanPurpose()));
    conditions.add(condition("propertyType", row.propertyType()));
    conditions.add(condition("occupancy", row.occupancy()));
    conditions.add(condition("units", row.units()));
    if (!"ALL".equalsIgnoreCase(row.state())) conditions.add(condition("state", row.state()));
    if (!row.county().isBlank()) conditions.add(condition("county", row.county()));

    String exclusivity = exclusivityGroup(config.investor(), row);
    String reasonCode = exclusivity + "_" + row.ficoBand().replaceAll("[^A-Za-z0-9]+", "_") + "_" + row.ltvBand().replaceAll("[^A-Za-z0-9]+", "_");
    return new AdjustmentRuleDraft(
        UUID.nameUUIDFromBytes((config.tenantId() + row.cellKey()).getBytes(StandardCharsets.UTF_8)),
        conditions.size(),
        conditions,
        new AdjustmentOutputDraft(config.outputType(), row.llpaBps()),
        reasonCode,
        exclusivity,
        true,
        "llpa-grid-row:" + row.sourceRow());
  }

  private static AdjustmentConditionDraft condition(String dimension, String value) {
    return new AdjustmentConditionDraft(dimension, "EQ", List.of(value));
  }

  private static String exclusivityGroup(String investor, LlpaGridRow row) {
    String suffix = "CORE";
    if (row.loanPurpose().contains("CASH_OUT")) suffix = "CASH_OUT";
    else if (!Set.of("SFR", "SINGLE_FAMILY").contains(row.propertyType())) suffix = "PROPERTY_TYPE";
    else if (!"1".equals(row.units())) suffix = "UNITS";
    else if (!"ALL".equalsIgnoreCase(row.state()) || !row.county().isBlank()) suffix = "GEOGRAPHY";
    return investor.toUpperCase(Locale.ROOT) + "_LLPA_" + suffix;
  }

  private static String normalizeHeader(String header) {
    return header == null ? "" : header.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private record ParsedGrid(List<Map<String, String>> rows, List<MappingWarning> warnings) {}

  private record LlpaGridRow(int sourceRow, String ficoBand, String ltvBand, String loanPurpose, String propertyType, String occupancy,
                             String units, String state, String county, BigDecimal llpaBps, int version) {
    static LlpaGridRow from(Map<String, String> row, int sourceRow) {
      String fico = required(row, "FICO_BAND");
      String ltv = required(row, "LTV_BAND");
      String purpose = required(row, "LOAN_PURPOSE").toUpperCase(Locale.ROOT);
      String propertyType = required(row, "PROPERTY_TYPE").toUpperCase(Locale.ROOT);
      String occupancy = required(row, "OCCUPANCY").toUpperCase(Locale.ROOT);
      String units = required(row, "UNITS");
      BigDecimal bps;
      try { bps = new BigDecimal(required(row, "LLPA_BPS")).setScale(1, RoundingMode.HALF_UP); }
      catch (NumberFormatException ex) { throw new IllegalArgumentException("LLPA_BPS must be numeric"); }
      int version = 0;
      if (!row.getOrDefault("VERSION", "").isBlank()) {
        try { version = Integer.parseInt(row.get("VERSION")); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("VERSION must be numeric when provided"); }
      }
      return new LlpaGridRow(sourceRow, fico, ltv, purpose, propertyType, occupancy, units,
          Optional.ofNullable(row.get("STATE")).filter(s -> !s.isBlank()).orElse("ALL").toUpperCase(Locale.ROOT),
          Optional.ofNullable(row.get("COUNTY")).orElse("").toUpperCase(Locale.ROOT), bps, version);
    }

    String cellKey() {
      return String.join("|", ficoBand, ltvBand, loanPurpose, propertyType, occupancy, units, state, county);
    }

    private static String required(Map<String, String> row, String column) {
      String value = row.get(column);
      if (value == null || value.isBlank()) throw new IllegalArgumentException(column + " is required");
      return value.trim();
    }
  }

  public record MappingConfig(UUID tenantId, UUID rateSheetId, String investor, String productFamily, String channel, String outputType,
                              Instant effectiveAt, String versionLabel, String sourceSystem) {
    static MappingConfig normalize(MappingConfig config) {
      Objects.requireNonNull(config, "mappingConfig is required");
      return new MappingConfig(
          Objects.requireNonNull(config.tenantId(), "tenantId is required"),
          config.rateSheetId(),
          required(config.investor(), "investor"),
          defaulted(config.productFamily(), "CONVENTIONAL"),
          defaulted(config.channel(), "RETAIL"),
          defaulted(config.outputType(), "BPS_DELTA"),
          config.effectiveAt() == null ? Instant.now() : config.effectiveAt(),
          defaulted(config.versionLabel(), "v1"),
          defaulted(config.sourceSystem(), "rate-feed-service"));
    }

    private static String required(String value, String field) {
      if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
      return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String defaulted(String value, String fallback) {
      return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
  }

  public record AdjustmentRuleBookDraft(UUID tenantId, UUID ruleBookId, String businessKey, String version, String status,
                                        RuleBookSelectorDraft selector, EffectiveWindowDraft effectiveWindow,
                                        PrecisionPolicyDraft precisionPolicy, List<AdjustmentRuleDraft> rules,
                                        String gridHash, int sourceRowCount, List<MappingWarning> warnings,
                                        Map<String, String> metadata) {}
  public record RuleBookSelectorDraft(String productFamily, String investor, String channel) {}
  public record EffectiveWindowDraft(Instant start, Instant end) {}
  public record PrecisionPolicyDraft(int pointsScale, int bpsScale, int moneyScale, String roundingMode) {}
  public record AdjustmentRuleDraft(UUID ruleId, int priority, List<AdjustmentConditionDraft> conditions, AdjustmentOutputDraft output,
                                    String reasonCode, String exclusivityGroup, boolean enabled, String sourceRef) {}
  public record AdjustmentConditionDraft(String dimension, String operator, List<String> configuredValues) {}
  public record AdjustmentOutputDraft(String type, BigDecimal configuredAmount) {}
  public record MappingWarning(int rowNumber, String code, String message) {}
}
