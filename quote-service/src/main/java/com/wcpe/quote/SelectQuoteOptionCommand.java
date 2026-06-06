package com.wcpe.quote;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SelectQuoteOptionCommand(
    UUID tenantId,
    UUID quoteId,
    UUID optionId,
    String selectionIntent,
    List<String> acknowledgements,
    String nonTopRankReason,
    QuoteSelectionPolicy policy,
    String actorId,
    String idempotencyKey,
    String correlationId,
    Map<String, String> clientContext
) {
    public SelectQuoteOptionCommand {
        acknowledgements = List.copyOf(acknowledgements == null ? List.of() : acknowledgements);
        clientContext = Map.copyOf(clientContext == null ? Map.of() : clientContext);
    }
}
