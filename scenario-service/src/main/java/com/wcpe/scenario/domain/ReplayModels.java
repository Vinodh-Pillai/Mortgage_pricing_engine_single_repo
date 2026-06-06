package com.wcpe.scenario.domain;

import java.time.*;
import java.util.*;

// S10: Scenario Replay Package extended records

enum RedactionMode { ROLE_DEFAULT, FULL, REDACTED }

record ReplayPackage(UUID scenarioId, int scenarioVersion, String schemaVersion, boolean redactionApplied, ScenarioStatus status,
    List<VersionManifest> versionManifest, Map<String, Object> rawInputSnapshot, Map<String, Object> normalizedSnapshot,
    List<ValidationIssue> validationIssues, List<EventRecord> eventReferences, UUID auditPackageId) {}

record ReplayAccessLogEntry(UUID accessId, UUID tenantId, UUID scenarioId, int scenarioVersion, String actorId,
    String redactionMode, boolean exportFlag, String accessReasonCode, String correlationId, Instant accessedAtUtc) {}

record ReplayHashVerification(boolean verified, String expectedHash, String actualHash, List<String> warnings) {}

record ScenarioReplayAccessRequest(String version, String redaction, boolean export, String accessReasonCode, String correlationId) {}
