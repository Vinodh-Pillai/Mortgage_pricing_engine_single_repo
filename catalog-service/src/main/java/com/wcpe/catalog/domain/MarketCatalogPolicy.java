package com.wcpe.catalog.domain;

import java.time.*;
import java.util.*;

final class MarketCatalogPolicy {
  private static final Map<String, String> STATE_FIPS = Map.ofEntries(
      Map.entry("AL", "01"), Map.entry("AK", "02"), Map.entry("AZ", "04"), Map.entry("AR", "05"),
      Map.entry("CA", "06"), Map.entry("CO", "08"), Map.entry("CT", "09"), Map.entry("DE", "10"),
      Map.entry("DC", "11"), Map.entry("FL", "12"), Map.entry("GA", "13"), Map.entry("HI", "15"),
      Map.entry("ID", "16"), Map.entry("IL", "17"), Map.entry("IN", "18"), Map.entry("IA", "19"),
      Map.entry("KS", "20"), Map.entry("KY", "21"), Map.entry("LA", "22"), Map.entry("ME", "23"),
      Map.entry("MD", "24"), Map.entry("MA", "25"), Map.entry("MI", "26"), Map.entry("MN", "27"),
      Map.entry("MS", "28"), Map.entry("MO", "29"), Map.entry("MT", "30"), Map.entry("NE", "31"),
      Map.entry("NV", "32"), Map.entry("NH", "33"), Map.entry("NJ", "34"), Map.entry("NM", "35"),
      Map.entry("NY", "36"), Map.entry("NC", "37"), Map.entry("ND", "38"), Map.entry("OH", "39"),
      Map.entry("OK", "40"), Map.entry("OR", "41"), Map.entry("PA", "42"), Map.entry("RI", "44"),
      Map.entry("SC", "45"), Map.entry("SD", "46"), Map.entry("TN", "47"), Map.entry("TX", "48"),
      Map.entry("UT", "49"), Map.entry("VT", "50"), Map.entry("VA", "51"), Map.entry("WA", "53"),
      Map.entry("WV", "54"), Map.entry("WI", "55"), Map.entry("WY", "56"));
  private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED", "RESTRICTED");

  private MarketCatalogPolicy() {}

  static String requireStateCode(String stateCode) {
    String normalized = stateCode == null ? "" : stateCode.trim().toUpperCase(Locale.ROOT);
    if (!STATE_FIPS.containsKey(normalized)) throw new CatalogException("INVALID_STATE_CODE");
    return normalized;
  }

  static String normalizeCountyFips(String stateCode, String countyFips) {
    if (countyFips == null || countyFips.isBlank()) return null;
    String normalized = countyFips.trim();
    if (!normalized.matches("\\d{5}") || !normalized.startsWith(STATE_FIPS.get(requireStateCode(stateCode)))) throw new CatalogException("INVALID_COUNTY_FIPS");
    return normalized;
  }

  static String requireStatus(String marketStatus) {
    String normalized = marketStatus == null || marketStatus.isBlank() ? "ENABLED" : marketStatus.trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(normalized)) throw new CatalogException("INVALID_MARKET_STATUS");
    return normalized;
  }

  static void requireResolvable(MarketResolveRequest request) {
    if (request == null) throw new CatalogException("MARKET_RESOLVE_REQUEST_REQUIRED");
    requireStateCode(request.stateCode());
    normalizeCountyFips(request.stateCode(), request.countyFips());
  }

  static Map<String, Object> changedPayload(UUID tenantId, UUID marketVersionId, MarketArea market, int versionNumber, String configHash) {
    Map<String, Object> effectiveWindow = new LinkedHashMap<>();
    effectiveWindow.put("start", market.effectiveFrom() == null ? null : market.effectiveFrom().atStartOfDay().toInstant(ZoneOffset.UTC).toString());
    effectiveWindow.put("end", market.effectiveTo() == null ? null : market.effectiveTo().atStartOfDay().toInstant(ZoneOffset.UTC).toString());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventKey", tenantId + ":" + market.stateCode() + ":" + Objects.toString(market.countyFips(), "*"));
    payload.put("marketVersionId", marketVersionId.toString());
    payload.put("versionNumber", versionNumber);
    payload.put("stateCode", market.stateCode());
    payload.put("countyFips", market.countyFips());
    payload.put("marketStatus", market.marketStatus());
    payload.put("status", market.marketStatus());
    payload.put("restrictionReasonCode", market.restrictionReasonCode());
    payload.put("effectiveWindow", effectiveWindow);
    payload.put("configHash", configHash);
    return payload;
  }
}
