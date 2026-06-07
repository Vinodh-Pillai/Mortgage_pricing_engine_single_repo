package com.wcpe.observability.scenariohash;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public final class ScenarioHashService {
  private static final List<String> SAFE_TELEMETRY_FIELDS = List.of(
      "scenarioHash",
      "hashSchemaVersion",
      "canonicalPayloadSha256",
      "versionGraph",
      "cacheEligibility",
      "metrics:scenario.hash.compute.latency",
      "metrics:scenario.hash.cache_eligible.count",
      "trace:pricing.scenario.hash");

  public String canonicalize(CanonicalPricingScenario scenario) {
    return canonicalizeWithMetadata(scenario).canonicalJson();
  }

  public ScenarioHash hash(CanonicalPricingScenario scenario) {
    CanonicalPayload payload = canonicalizeWithMetadata(scenario);
    return new ScenarioHash(sha256Hex(
        scenario.hashSchemaVersion().value() + ":" + payload.canonicalJson()));
  }

  public ScenarioHashExplanation explain(CanonicalPricingScenario scenario) {
    CanonicalPayload payload = canonicalizeWithMetadata(scenario);
    ScenarioHash scenarioHash = new ScenarioHash(sha256Hex(
        scenario.hashSchemaVersion().value() + ":" + payload.canonicalJson()));
    return new ScenarioHashExplanation(
        scenarioHash,
        scenario.hashSchemaVersion(),
        sha256Hex(payload.canonicalJson()),
        scenario.versionGraph().versions(),
        true,
        payload.exclusions(),
        payload.includedFields(),
        SAFE_TELEMETRY_FIELDS,
        "Compare hash schema version, version graph, and canonical payload digest; never log raw canonical JSON.");
  }

  private CanonicalPayload canonicalizeWithMetadata(CanonicalPricingScenario scenario) {
    Objects.requireNonNull(scenario, "scenario is required");
    List<HashExclusionReason> exclusions = new ArrayList<>();
    List<String> includedFields = new ArrayList<>();
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("hashSchemaVersion", scenario.hashSchemaVersion().value());
    root.put("pricingInput", scenario.pricingInput());
    root.put("tenantId", scenario.tenantId().toString());
    root.put("versionGraph", scenario.versionGraph().versions());
    String canonicalJson = canonicalJson(root, "$", exclusions, includedFields);
    return new CanonicalPayload(canonicalJson, List.copyOf(exclusions), List.copyOf(includedFields));
  }

  private static String canonicalJson(
      Object value,
      String path,
      List<HashExclusionReason> exclusions,
      List<String> includedFields) {
    if (value == null) {
      includedFields.add(path);
      return "null";
    }
    if (value instanceof String string) {
      includedFields.add(path);
      return quote(string.strip());
    }
    if (value instanceof UUID uuid) {
      includedFields.add(path);
      return quote(uuid.toString());
    }
    if (value instanceof Boolean bool) {
      includedFields.add(path);
      return bool.toString().toLowerCase(Locale.ROOT);
    }
    if (value instanceof BigDecimal decimal) {
      includedFields.add(path);
      return quote(decimal.stripTrailingZeros().toPlainString());
    }
    if (value instanceof BigInteger integer) {
      includedFields.add(path);
      return integer.toString();
    }
    if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
      includedFields.add(path);
      return value.toString();
    }
    if (value instanceof Float || value instanceof Double) {
      throw new IllegalArgumentException(path + " uses binary floating point; use BigDecimal for pricing values");
    }
    if (value instanceof Instant instant) {
      includedFields.add(path);
      return quote(instant.toString());
    }
    if (value instanceof OffsetDateTime dateTime) {
      includedFields.add(path);
      return quote(dateTime.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
    }
    if (value instanceof ZonedDateTime dateTime) {
      includedFields.add(path);
      return quote(dateTime.withZoneSameInstant(ZoneOffset.UTC).toInstant().toString());
    }
    if (value instanceof TemporalAccessor temporal) {
      includedFields.add(path);
      return quote(temporal.toString());
    }
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = Objects.requireNonNull(entry.getKey(), "canonical JSON keys are required").toString();
        String childPath = path + "." + key;
        if (isExcludedKey(key)) {
          exclusions.add(new HashExclusionReason(childPath, "direct PII or volatile metadata excluded from scenario hash"));
          continue;
        }
        sorted.put(key, entry.getValue());
      }
      StringBuilder builder = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<String, Object> entry : sorted.entrySet()) {
        if (!first) {
          builder.append(',');
        }
        first = false;
        builder.append(quote(entry.getKey()))
            .append(':')
            .append(canonicalJson(entry.getValue(), path + "." + entry.getKey(), exclusions, includedFields));
      }
      return builder.append('}').toString();
    }
    if (value instanceof Collection<?> collection) {
      StringBuilder builder = new StringBuilder("[");
      int index = 0;
      for (Object item : collection) {
        if (index > 0) {
          builder.append(',');
        }
        builder.append(canonicalJson(item, path + "[" + index + "]", exclusions, includedFields));
        index++;
      }
      return builder.append(']').toString();
    }
    if (value instanceof Enum<?> enumValue) {
      includedFields.add(path);
      return quote(enumValue.name().toLowerCase(Locale.ROOT));
    }
    throw new IllegalArgumentException(path + " uses unsupported canonical JSON value type: " + value.getClass().getName());
  }

  private static boolean isExcludedKey(String key) {
    String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    return List.of(
        "borrowername",
        "firstname",
        "middlename",
        "lastname",
        "fullname",
        "ssn",
        "socialsecuritynumber",
        "email",
        "emailaddress",
        "phone",
        "phonenumber",
        "mobilephone",
        "requesttimestamp",
        "requestedat",
        "correlationid",
        "uilocale",
        "locale",
        "authorization",
        "accesstoken",
        "token",
        "secret",
        "password")
        .contains(normalized);
  }

  private static String quote(String value) {
    StringBuilder builder = new StringBuilder("\"");
    for (int index = 0; index < value.length(); index++) {
      char ch = value.charAt(index);
      switch (ch) {
        case '\\' -> builder.append("\\\\");
        case '"' -> builder.append("\\\"");
        case '\b' -> builder.append("\\b");
        case '\f' -> builder.append("\\f");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (ch < 0x20) {
            builder.append(String.format("\\u%04x", (int) ch));
          } else {
            builder.append(ch);
          }
        }
      }
    }
    return builder.append('"').toString();
  }

  private static String sha256Hex(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        builder.append(String.format("%02x", item));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private record CanonicalPayload(
      String canonicalJson,
      List<HashExclusionReason> exclusions,
      List<String> includedFields) {}
}
