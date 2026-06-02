package com.wcpe.eligibility.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class PropertyTypeMetrics {

    private final MeterRegistry registry;
    private final Counter projectReviewWarningTotal;

    public PropertyTypeMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.projectReviewWarningTotal = Counter.builder("property_type_project_review_warning_total")
            .description("Total project review warnings issued")
            .register(registry);
    }

    public void recordDecision(String status, String reason) {
        Counter.builder("property_type_decision_total")
            .description("Total property type decisions by status and reason")
            .tags("status", status, "reason", reason != null ? reason : "none")
            .register(registry)
            .increment();
    }

    public void recordProjectReviewWarning() {
        projectReviewWarningTotal.increment();
    }

    public boolean isProjectReviewWarning(String reasonCode) {
        return reasonCode != null && reasonCode.contains("PROJECT_REVIEW");
    }
}
