package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.domain.QuoteReplayStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class QuoteReplayMetrics {

    public static final String DURATION = "quote_replay_duration_ms";
    public static final String MATCH_TOTAL = "quote_replay_match_total";
    public static final String MISMATCH_TOTAL = "quote_replay_mismatch_total";
    public static final String FAILED_TOTAL = "quote_replay_failed_total";

    private final MeterRegistry registry;

    public QuoteReplayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    QuoteReplayMetrics() {
        this.registry = null;
    }

    void record(QuoteReplayStatus status, Duration duration) {
        if (registry == null) {
            return;
        }
        registry.timer(DURATION).record(duration);
        if (status == QuoteReplayStatus.MATCH) {
            registry.counter(MATCH_TOTAL).increment();
        } else if (status == QuoteReplayStatus.MISMATCH) {
            registry.counter(MISMATCH_TOTAL).increment();
        } else if (status == QuoteReplayStatus.FAILED) {
            registry.counter(FAILED_TOTAL).increment();
        }
    }
}
