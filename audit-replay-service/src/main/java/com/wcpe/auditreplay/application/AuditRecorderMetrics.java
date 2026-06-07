package com.wcpe.auditreplay.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class AuditRecorderMetrics {

    static final String RECORDS_WRITTEN = "audit_records_written_total";
    static final String WRITE_LATENCY = "audit_record_write_latency_ms";
    static final String REDACTION_FAILURES = "audit_redaction_failures_total";
    static final String HASH_CHAIN_BREAKS = "audit_hash_chain_breaks_total";

    private final MeterRegistry meterRegistry;

    public AuditRecorderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        registerMeters();
    }

    void recordWrite(Duration duration) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(RECORDS_WRITTEN).description("Audit records persisted").register(meterRegistry).increment();
        Timer.builder(WRITE_LATENCY).description("Audit record write latency").register(meterRegistry).record(duration);
    }

    void recordRedactionFailure() {
        if (meterRegistry != null) {
            Counter.builder(REDACTION_FAILURES).description("Audit snapshot redaction failures").register(meterRegistry).increment();
        }
    }

    void recordHashChainBreak() {
        if (meterRegistry != null) {
            Counter.builder(HASH_CHAIN_BREAKS).description("Audit hash chain continuity failures").register(meterRegistry).increment();
        }
    }

    private void registerMeters() {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(RECORDS_WRITTEN).description("Audit records persisted").register(meterRegistry);
        Timer.builder(WRITE_LATENCY).description("Audit record write latency").register(meterRegistry);
        Counter.builder(REDACTION_FAILURES).description("Audit snapshot redaction failures").register(meterRegistry);
        Counter.builder(HASH_CHAIN_BREAKS).description("Audit hash chain continuity failures").register(meterRegistry);
    }
}
