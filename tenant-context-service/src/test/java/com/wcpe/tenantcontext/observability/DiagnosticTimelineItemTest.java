package com.wcpe.tenantcontext.observability;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class DiagnosticTimelineItemTest {
    @Test
    void capturesRedactedLocalTimelineItemWithoutRawPayload() {
        DiagnosticTimelineItem item = new DiagnosticTimelineItem("audit", "tenant-context-service", "corr-1",
            "cause-1", "audit-1", "SUCCESS", "audit write completed", Instant.parse("2026-06-08T02:15:00Z"), "redacted");

        assertThat(item.correlationId()).isEqualTo("corr-1");
        assertThat(item.summary()).isEqualTo("audit write completed");
        assertThat(item.redactionLevel()).isEqualTo("redacted");
    }
}
