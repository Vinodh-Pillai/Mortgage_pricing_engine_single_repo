package com.wcpe.exception.domain;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory repository for exception requests.
 * Mock-backed only; no persistence in this PII.
 */
public class ExceptionRepository {

  private final ConcurrentHashMap<String, ExceptionModels.ExceptionRequestRecord> store = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong(0);

  public ExceptionModels.ExceptionRequestRecord create(ExceptionModels.ExceptionRequestCreate request) {
    String id = "EXC-" + sequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.ExceptionRequestRecord record = new ExceptionModels.ExceptionRequestRecord(
      id,
      request.placeholderQuoteReference(),
      request.requestType(),
      ExceptionModels.ExceptionState.DRAFT,
      now,
      now
    );
    store.put(id, record);
    return record;
  }

  public Optional<ExceptionModels.ExceptionRequestRecord> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }

  public Optional<ExceptionModels.ExceptionRequestRecord> transition(String id, ExceptionModels.ExceptionState target) {
    ExceptionModels.ExceptionRequestRecord existing = store.get(id);
    if (existing == null) return Optional.empty();

    Set<ExceptionModels.ExceptionState> allowed = ExceptionModels.ExceptionState.allowedTransitions(existing.state());
    if (!allowed.contains(target)) {
      throw new ExceptionServiceException(
        "INVALID_TRANSITION",
        String.format("Cannot transition from %s to %s", existing.state(), target)
      );
    }

    Instant now = Instant.now();
    ExceptionModels.ExceptionRequestRecord updated = new ExceptionModels.ExceptionRequestRecord(
      existing.exceptionRequestId(),
      existing.placeholderQuoteReference(),
      existing.requestType(),
      target,
      existing.createdAt(),
      now
    );
    store.put(id, updated);
    return Optional.of(updated);
  }

  void clear() {
    store.clear();
    sequence.set(0);
  }
}
