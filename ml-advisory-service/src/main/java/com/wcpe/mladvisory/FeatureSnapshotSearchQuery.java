package com.wcpe.mladvisory;

import java.time.Instant;

public record FeatureSnapshotSearchQuery(String tenantId, String scenarioId, Instant from, Instant to, String correlationId) {}
