package com.wcpe.ratefeed.audit;

import com.wcpe.ratefeed.domain.Hashing;

import java.time.Instant;
import java.util.UUID;

public final class ReplayHashVerifier {
  private static final int DRIFT_TOLERANCE = 4;

  private ReplayHashVerifier() {}

  public static ReplayVerificationResult verify(UUID verificationId, String expectedHash, String actualHash,
      UUID batchId, String actorId, UUID correlationId) {
    Instant createdAt = Instant.now();
    if (expectedHash == null || actualHash == null) {
      return new ReplayVerificationResult(verificationId, batchId, "FAILED", "UNKNOWN", createdAt);
    }

    String expected = normalizeHash(expectedHash);
    String actual = normalizeHash(actualHash);
    if (expected.equals(actual)) {
      return new ReplayVerificationResult(verificationId, batchId, "PASSED", null, createdAt);
    }

    String classification = hashDistance(expected, actual) <= DRIFT_TOLERANCE ? "DRIFT" : "TAMPERED";
    return new ReplayVerificationResult(verificationId, batchId, "FAILED", classification, createdAt);
  }

  private static String normalizeHash(String hash) {
    return hash.startsWith("sha256:") ? hash : Hashing.sha256(hash);
  }

  private static int hashDistance(String expected, String actual) {
    int distance = Math.abs(expected.length() - actual.length());
    for (int i = 0; i < Math.min(expected.length(), actual.length()); i++) {
      if (expected.charAt(i) != actual.charAt(i)) distance++;
    }
    return distance;
  }

  public record ReplayVerificationResult(UUID verificationId, UUID batchId, String status,
      String mismatchClassification, Instant createdAt) {}
}
