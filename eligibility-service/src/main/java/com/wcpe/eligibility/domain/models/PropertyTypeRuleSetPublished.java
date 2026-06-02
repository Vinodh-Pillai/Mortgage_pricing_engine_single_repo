package com.wcpe.eligibility.domain.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PropertyTypeRuleSetPublished(
    UUID eventId,
    String eventType,
    String eventVersion,
    UUID tenantId,
    UUID ruleSetId,
    int version,
    String productCode,
    String investorCode,
    String channel,
    String actorId,
    String correlationId,
    Instant occurredAt
) {}
