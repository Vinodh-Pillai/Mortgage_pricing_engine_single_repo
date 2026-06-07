package com.wcpe.mladvisory;

import java.time.Instant;

public record MonitoringRunQuery(String tenantId, String modelVersionId, Instant from, Instant to) {}
