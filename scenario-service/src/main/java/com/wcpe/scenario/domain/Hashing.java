package com.wcpe.scenario.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Hashing {
  private Hashing() {}

  public static String sha256(String value) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder("sha256:");
      for (byte b : hash) builder.append(String.format("%02x", b));
      return builder.toString();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
