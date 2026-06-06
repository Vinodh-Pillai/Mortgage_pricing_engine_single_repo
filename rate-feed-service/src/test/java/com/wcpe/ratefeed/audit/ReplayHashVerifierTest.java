package com.wcpe.ratefeed.audit;

import com.wcpe.ratefeed.domain.Hashing;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplayHashVerifierTest {

  @Test
  void verify_matching_hashes_pass() {
    String hash = prefixed("a");

    ReplayHashVerifier.ReplayVerificationResult result = verify(hash, hash);

    assertEquals("PASSED", result.status());
    assertNull(result.mismatchClassification());
  }

  @Test
  void verify_null_expected_hash_fails_unknown() {
    ReplayHashVerifier.ReplayVerificationResult result = verify(null, prefixed("a"));

    assertEquals("FAILED", result.status());
    assertEquals("UNKNOWN", result.mismatchClassification());
  }

  @Test
  void verify_null_actual_hash_fails_unknown() {
    ReplayHashVerifier.ReplayVerificationResult result = verify(prefixed("a"), null);

    assertEquals("FAILED", result.status());
    assertEquals("UNKNOWN", result.mismatchClassification());
  }

  @Test
  void verify_drift_classification() {
    ReplayHashVerifier.ReplayVerificationResult result = verify(prefixed("a"), nearHash());

    assertEquals("FAILED", result.status());
    assertEquals("DRIFT", result.mismatchClassification());
  }

  @Test
  void verify_tampered_classification() {
    ReplayHashVerifier.ReplayVerificationResult result = verify(prefixed("a"), prefixed("f"));

    assertEquals("FAILED", result.status());
    assertEquals("TAMPERED", result.mismatchClassification());
  }

  @Test
  void verify_sha256_normalized() {
    String expected = Hashing.sha256("same-payload");

    ReplayHashVerifier.ReplayVerificationResult result = verify(expected, "same-payload");

    assertEquals("PASSED", result.status());
  }

  @Test
  void verify_result_has_correct_fields() {
    UUID verificationId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();

    ReplayHashVerifier.ReplayVerificationResult result = ReplayHashVerifier.verify(verificationId,
        prefixed("a"), prefixed("a"), batchId, "actor-1", correlationId);

    assertEquals(verificationId, result.verificationId());
    assertEquals(batchId, result.batchId());
    assertEquals("PASSED", result.status());
    assertNull(result.mismatchClassification());
    assertNotNull(result.createdAt());
  }

  private ReplayHashVerifier.ReplayVerificationResult verify(String expectedHash, String actualHash) {
    return ReplayHashVerifier.verify(UUID.randomUUID(), expectedHash, actualHash, UUID.randomUUID(),
        "actor-1", UUID.randomUUID());
  }

  private String prefixed(String value) {
    return "sha256:" + value.repeat(64).substring(0, 64);
  }

  private String nearHash() {
    return "sha256:" + "a".repeat(63) + "b";
  }
}
