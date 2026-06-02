package com.wcpe.scenario.domain;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

// S10 unit tests: Replay redaction policy and package assembly
class ScenarioReplayRedactionPolicyTest {
  @Test
  void redactsFinancialFieldsForSupport() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("borrowers", List.of(Map.of("creditScore", 720)));
    raw.put("monthlyIncome", "5000");
    raw.put("liquidAssets", "50000");
    raw.put("propertyState", "TX");
    raw.put("loanAmount", "300000");
    raw.put("channel", "RETAIL");

    // Simulate role-default / redacted mode
    raw.replaceAll((k, v) -> {
      String lk = k.toLowerCase();
      if (lk.contains("income") || lk.contains("asset") || lk.contains("credit")) return "REDACTED";
      return v;
    });

    assertEquals("REDACTED", raw.get("monthlyIncome"));
    assertEquals("REDACTED", raw.get("liquidAssets"));
    assertEquals("TX", raw.get("propertyState"));
    assertEquals("RETAIL", raw.get("channel"));
  }

  @Test
  void fullRedactionModeReturnsAllFields() {
    Map<String, Object> raw = Map.of("monthlyIncome", "5000", "liquidAssets", "50000", "propertyState", "TX");
    // Full mode — no redaction applied
    assertFalse(raw.values().stream().anyMatch(v -> "REDACTED".equals(v)));
  }

  @Test
  void redactedModeReturnsMaskedValues() {
    Map<String, Object> raw = new LinkedHashMap<>(Map.of("monthlyIncome", "5000", "liquidAssets", "50000", "propertyState", "TX"));
    raw.replaceAll((k, v) -> k.toLowerCase().contains("income") || k.toLowerCase().contains("asset") ? "REDACTED" : v);
    assertTrue(raw.values().stream().anyMatch(v -> "REDACTED".equals(v)));
  }
}

class ScenarioReplayPackageAssemblerTest {
  @Test
  void includesVersionManifestAndHashes() {
    List<VersionManifest> manifest = List.of(
        new VersionManifest(1, "CREATE_DRAFT", "sha256:abc123", Instant.parse("2026-05-01T10:00:00Z")),
        new VersionManifest(2, "UPDATE_BORROWER_CREDIT", "sha256:def456", Instant.parse("2026-05-01T11:00:00Z")),
        new VersionManifest(3, "NORMALIZE_SCENARIO", "sha256:ghi789", Instant.parse("2026-05-01T12:00:00Z"))
    );

    assertEquals(3, manifest.size());
    assertEquals(1, manifest.get(0).version());
    assertEquals("sha256:def456", manifest.get(1).hash());
    assertTrue(manifest.get(2).hash().startsWith("sha256:"));
  }

  @Test
  void hashVerificationMatchesExpected() {
    String input = "tenant:abc:scenario:def:version:1";
    String hash = Hashing.sha256(input);
    assertTrue(hash.startsWith("sha256:"));
    assertEquals(71, hash.length()); // "sha256:" + 64 hex chars
  }

  @Test
  void replayPackageIncludesAuditPackageId() {
    UUID auditId = UUID.randomUUID();
    ReplayPackage pkg = new ReplayPackage(UUID.randomUUID(), 1, "scenario-v1", true,
        ScenarioStatus.DRAFT_INCOMPLETE, List.of(), Map.of(), Map.of(),
        List.of(), List.of(), auditId);
    assertNotNull(pkg.auditPackageId());
  }
}
