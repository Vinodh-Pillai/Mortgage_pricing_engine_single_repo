package com.wcpe.quote;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuoteCreateRequest(
    UUID tenantId,
    UUID scenarioId,
    int scenarioVersion,
    List<Integer> requestedLockPeriods,
    QuoteFilters filters,
    String presentationCurrency,
    Map<String, String> clientContext,
    String actorId,
    String idempotencyKey,
    String correlationId,
    LocalDate effectiveDate
) {
    public QuoteCreateRequest {
        requestedLockPeriods = List.copyOf(requestedLockPeriods == null ? List.of() : requestedLockPeriods);
        clientContext = Map.copyOf(clientContext == null ? Map.of() : clientContext);
    }
}
