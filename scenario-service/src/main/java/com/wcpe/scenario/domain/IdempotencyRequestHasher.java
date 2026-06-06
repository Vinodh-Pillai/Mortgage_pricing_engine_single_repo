package com.wcpe.scenario.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

final class IdempotencyRequestHasher {
  private IdempotencyRequestHasher() {}

  static String hash(ObjectMapper mapper, String commandScope, Object request) {
    try {
      Object payload = request == null ? Map.of() : request;
      return Hashing.sha256(commandScope + ":" + mapper.writeValueAsString(payload));
    } catch (Exception ex) {
      throw new IllegalStateException("IDEMPOTENCY_HASH_FAILED", ex);
    }
  }
}
