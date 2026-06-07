package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class IntegrationContractFixtureSupport {
  static final List<String> FORBIDDEN_KEYS = List.of("\"password\"", "\"secret\"", "\"privatekey\"", "\"apikey\"", "\"authorization\"", "\"signature\"", "\"ssn\"");
  static final List<String> ERROR_REASONS =
      List.of("VALIDATION_FAILED", "UNAUTHENTICATED", "TENANT_ACCESS_DENIED", "NOT_FOUND", "VERSION_CONFLICT", "IDEMPOTENCY_CONFLICT", "POLICY_NOT_SATISFIED", "DEPENDENCY_UNAVAILABLE");

  private IntegrationContractFixtureSupport() {}

  static String readContract(String relativePath) {
    Path path = Path.of("contracts").resolve(relativePath);
    assertTrue(Files.exists(path), () -> "missing contract fixture " + path);
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError("failed to read contract fixture " + path, e);
    }
  }

  static void assertContains(String relativePath, String... expectedFragments) {
    String content = readContract(relativePath);
    for (String expected : expectedFragments) {
      assertTrue(content.contains(expected), () -> relativePath + " missing " + expected);
    }
  }

  static void assertEventSchema(String relativePath, String... eventTypes) {
    assertContains(relativePath, "tenantId", "eventId", "eventType", "eventVersion", "sourceService", "actorId", "correlationId", "causationId", "idempotencyKey", "occurredAt", "payloadHash");
    assertContains(relativePath, eventTypes);
  }

  static void assertNoForbiddenKeysIn(String relativePath) {
    String lower = readContract(relativePath).toLowerCase(Locale.ROOT);
    for (String forbidden : FORBIDDEN_KEYS) {
      assertFalse(lower.contains(forbidden), () -> relativePath + " exposes forbidden token " + forbidden);
    }
  }

  static void assertAllFixturesRedacted() throws IOException {
    try (Stream<Path> paths = Files.walk(Path.of("contracts"))) {
      List<Path> files = paths.filter(Files::isRegularFile).toList();
      assertFalse(files.isEmpty(), "contract fixture catalog must not be empty");
      for (Path file : files) {
        String lower = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_KEYS) {
          assertFalse(lower.contains(forbidden), () -> file + " exposes forbidden token " + forbidden);
        }
      }
    }
  }
}
