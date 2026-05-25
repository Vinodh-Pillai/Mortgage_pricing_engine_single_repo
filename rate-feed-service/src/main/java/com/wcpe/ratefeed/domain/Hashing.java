package com.wcpe.ratefeed.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.*;

public final class Hashing {
  private Hashing() {}

  public static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder("sha256:");
      for (byte b : digest) out.append(String.format("%02x", b));
      return out.toString();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * G-008: Deterministic grid hash.
   * Sorts RatePricePoint array by (noteRate ASC, lockPeriod ASC),
   * serializes to JSON, then SHA-256.
   */
  public static String gridHash(ObjectMapper mapper, Collection<RateFeedModels.RatePricePoint> points) {
    List<RateFeedModels.RatePricePoint> sorted = points.stream()
        .sorted(Comparator.<RateFeedModels.RatePricePoint, BigDecimal>comparing(p -> p.noteRate())
             .thenComparingInt(p -> p.lockPeriod()))
        .toList();
    try {
      String json = mapper.writeValueAsString(sorted);
      return sha256(json);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
