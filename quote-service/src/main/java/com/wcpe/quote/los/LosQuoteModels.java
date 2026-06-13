package com.wcpe.quote.los;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class LosQuoteModels {
  private LosQuoteModels() {
  }

  public record LosQuoteRequest(
      String tenantId,
      String scenarioId,
      int scenarioVersion,
      List<Integer> requestedLockPeriods,
      Map<String, String> clientContext,
      String actorId,
      String idempotencyKey,
      String correlationId,
      LocalDate effectiveDate,
      boolean preferAsync) {
    public LosQuoteRequest {
      requestedLockPeriods = List.copyOf(requestedLockPeriods == null ? List.of() : requestedLockPeriods);
      clientContext = Map.copyOf(clientContext == null ? Map.of() : clientContext);
    }
  }

  public record LosQuoteResponse(
      String jobId,
      String status,
      String statusUrl,
      String correlationId,
      Instant acceptedAt,
      Map<String, String> progress) {
    public LosQuoteResponse {
      progress = Map.copyOf(progress == null ? Map.of() : progress);
    }
  }

  public record LosQuoteError(String code, String message, String correlationId) {
  }
}
