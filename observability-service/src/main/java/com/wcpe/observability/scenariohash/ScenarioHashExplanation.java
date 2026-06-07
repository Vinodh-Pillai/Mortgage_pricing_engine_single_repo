package com.wcpe.observability.scenariohash;

import java.util.List;
import java.util.Map;

public record ScenarioHashExplanation(
    ScenarioHash scenarioHash,
    HashSchemaVersion hashSchemaVersion,
    String canonicalPayloadSha256,
    Map<String, String> versionGraph,
    boolean cacheEligible,
    List<HashExclusionReason> exclusions,
    List<String> includedFingerprintFields,
    List<String> safeTelemetryFields,
    String runbookNote) {}
