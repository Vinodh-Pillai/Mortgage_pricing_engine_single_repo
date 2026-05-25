package com.wcpe.ratefeed.hashing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.domain.RatePricePoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hash determinism tests — G-008
 * Tests: shuffled CSV same hash, different content different hash
 * Uses ReflectionTestHelper to access package-private Hashing methods
 */
class HashingNewTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void gridHash_samePointsDifferentOrder_sameHash() {
    List<RatePricePoint> a = List.of(
        pp(new BigDecimal("6.5"), 30, "108.5"),
        pp(new BigDecimal("7.0"), 30, "110.0")
    );
    List<RatePricePoint> b = List.of(  // reversed order
        pp(new BigDecimal("7.0"), 30, "110.0"),
        pp(new BigDecimal("6.5"), 30, "108.5")
    );
    String hashA = gridHash(a);
    String hashB = gridHash(b);
    assertEquals(hashA, hashB, "Grid hash must be identical regardless of row order");
  }

  @Test
  void gridHash_differentContent_differentHash() {
    List<RatePricePoint> a = List.of(pp(new BigDecimal("6.5"), 30, "100.0"));
    List<RatePricePoint> b = List.of(pp(new BigDecimal("6.5"), 30, "200.0"));
    String hashA = gridHash(a);
    String hashB = gridHash(b);
    assertNotEquals(hashA, hashB, "Different content must produce different hashes");
  }

  @Test
  void gridHash_deterministic_acrossCalls() {
    List<RatePricePoint> pts = List.of(pp(new BigDecimal("6.5"), 30, "108.5"));
    String h1 = gridHash(pts);
    String h2 = gridHash(pts);
    assertEquals(h1, h2, "Same input must produce same hash");
  }

  @Test
  void sha256_format_isCorrect() {
    // Access package-private sha256
    String hex = sha256("test-string");
    // SHA-256 = 256 bits = 64 hex chars
    assertEquals(64, hex.length(), "SHA-256 hex should be 64 characters");
    // All hex chars
    assertTrue(hex.matches("[0-9a-f]+"), "Hash should be lowercase hex");
  }

  @Test
  void sha256_differentInput_differentHash() {
    assertNotEquals(sha256("a"), sha256("b"));
  }

  private RatePricePoint pp(BigDecimal rate, int lock, String price) {
    return new RatePricePoint(
        UUID.randomUUID(), rate, lock, new BigDecimal(price), null, null, 1
    );
  }

  private String gridHash(List<RatePricePoint> points) {
    try {
      Method m = Class.forName("com.wcpe.ratefeed.domain.Hashing")
          .getDeclaredMethod("gridHash", ObjectMapper.class, java.util.Collection.class);
      m.setAccessible(true);
      return (String) m.invoke(null, mapper, points);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String sha256(String value) {
    try {
      Method m = Class.forName("com.wcpe.ratefeed.domain.Hashing")
          .getDeclaredMethod("sha256", String.class);
      m.setAccessible(true);
      return (String) m.invoke(null, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
