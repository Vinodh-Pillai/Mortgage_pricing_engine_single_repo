package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.domain.LockReplayStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class LockReplayMetrics {

    public static final String DURATION = "lock_replay_duration_ms";
    public static final String MATCH_TOTAL = "lock_replay_match_total";
    public static final String MISMATCH_TOTAL = "lock_replay_mismatch_total";
    public static final String MISSING_SNAPSHOT_TOTAL = "lock_replay_missing_snapshot_total";
    public static final String FAILED_TOTAL = "lock_replay_failed_total";

    private final MeterRegistry registry;

    public LockReplayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    LockReplayMetrics() {
        this.registry = null;
    }

    void record(LockReplayStatus status, Duration duration) {
        if (registry == null) {
            return;
        }
        registry.timer(DURATION).record(duration);
        if (status == LockReplayStatus.MATCH) {
            registry.counter(MATCH_TOTAL).increment();
        } else if (status == LockReplayStatus.MISMATCH) {
            registry.counter(MISMATCH_TOTAL).increment();
        } else if (status == LockReplayStatus.FAILED) {
            registry.counter(FAILED_TOTAL).increment();
        }
    }

    void recordMissingSnapshot() {
        if (registry != null) {
            registry.counter(MISSING_SNAPSHOT_TOTAL).increment();
        }
    }
}
