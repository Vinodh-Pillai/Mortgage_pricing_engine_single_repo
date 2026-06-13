package com.wcpe.pricingbff.los;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class LosIdempotencyStore {
  private static final Duration RETENTION = Duration.ofHours(24);
  private final Clock clock;
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();

  LosIdempotencyStore() {
    this(Clock.systemUTC());
  }

  LosIdempotencyStore(Clock clock) {
    this.clock = clock;
  }

  Optional<Object> cached(String method, String path, String key, String requestHash) {
    purgeExpired();
    Entry entry = entries.get(cacheKey(method, path, key));
    if (entry == null || entry.expiresAt().isBefore(clock.instant()) || !entry.requestHash().equals(requestHash)) {
      return Optional.empty();
    }
    return Optional.of(entry.response());
  }

  void store(String method, String path, String key, String requestHash, Object response) {
    entries.put(cacheKey(method, path, key), new Entry(requestHash, response, clock.instant().plus(RETENTION)));
  }

  private void purgeExpired() {
    Instant now = clock.instant();
    entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
  }

  private String cacheKey(String method, String path, String key) {
    return method + ":" + path + ":" + key;
  }

  private record Entry(String requestHash, Object response, Instant expiresAt) {
  }
}
