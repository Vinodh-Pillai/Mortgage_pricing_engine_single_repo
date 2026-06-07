package com.wcpe.auditreplay.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class EvidenceExportMetrics {

    public static final String DURATION = "evidence_export_duration_ms";
    public static final String SIZE_BYTES = "evidence_export_size_bytes";
    public static final String FAILED_TOTAL = "evidence_export_failed_total";
    public static final String DOWNLOAD_TOTAL = "evidence_export_download_total";

    private final MeterRegistry registry;

    public EvidenceExportMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    EvidenceExportMetrics() {
        this.registry = null;
    }

    void recordReady(Duration duration, long sizeBytes) {
        if (registry == null) {
            return;
        }
        registry.timer(DURATION).record(duration);
        registry.summary(SIZE_BYTES).record(sizeBytes);
    }

    void recordFailure() {
        if (registry != null) {
            registry.counter(FAILED_TOTAL).increment();
        }
    }

    public void recordDownload() {
        if (registry != null) {
            registry.counter(DOWNLOAD_TOTAL).increment();
        }
    }
}
