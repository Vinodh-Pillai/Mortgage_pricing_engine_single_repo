package com.wcpe.quote;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuoteJobStartRequest(
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
    LocalDate effectiveDate,
    boolean preferAsync
) {
    public QuoteJobStartRequest {
        requestedLockPeriods = List.copyOf(requestedLockPeriods == null ? List.of() : requestedLockPeriods);
        clientContext = Map.copyOf(clientContext == null ? Map.of() : clientContext);
    }

    public QuoteCreateRequest quoteRequest() {
        return new QuoteCreateRequest(
            tenantId,
            scenarioId,
            scenarioVersion,
            requestedLockPeriods,
            filters,
            presentationCurrency,
            clientContext,
            actorId,
            idempotencyKey,
            correlationId,
            effectiveDate
        );
    }
}
