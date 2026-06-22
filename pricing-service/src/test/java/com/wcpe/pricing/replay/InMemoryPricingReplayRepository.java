package com.wcpe.pricing.replay;

import com.wcpe.pricing.replay.PricingReplayApi.PricingCalculationSnapshot;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayAudit;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayDiff;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayEvent;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayRepository;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayRun;
import com.wcpe.pricing.replay.PricingReplayApi.ReplaySourceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPricingReplayRepository implements PricingReplayRepository {
    private final Map<String, PricingCalculationSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, PricingReplayRun> runs = new ConcurrentHashMap<>();
    private final List<PricingReplayDiff> diffs = new ArrayList<>();
    private final List<PricingReplayEvent> events = new ArrayList<>();
    private final List<PricingReplayAudit> audits = new ArrayList<>();

    public void saveSnapshot(PricingCalculationSnapshot snapshot) {
        snapshots.put(snapshotKey(snapshot.tenantId(), snapshot.sourceType(), snapshot.sourceId()), snapshot);
    }

    @Override
    public Optional<PricingCalculationSnapshot> findSnapshot(String tenantId, ReplaySourceType sourceType, String sourceId) {
        return Optional.ofNullable(snapshots.get(snapshotKey(tenantId, sourceType, sourceId)));
    }

    @Override
    public void saveRun(PricingReplayRun run) {
        runs.put(run.id(), run);
    }

    @Override
    public Optional<PricingReplayRun> findRun(String tenantId, UUID replayRunId) {
        PricingReplayRun run = runs.get(replayRunId);
        if (run == null || !tenantId.equals(run.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(run);
    }

    @Override
    public void saveDiff(PricingReplayDiff diff) {
        diffs.add(diff);
    }

    @Override
    public List<PricingReplayDiff> findDiffs(String tenantId, UUID replayRunId) {
        return diffs.stream()
                .filter(diff -> tenantId.equals(diff.tenantId()))
                .filter(diff -> replayRunId.equals(diff.replayRunId()))
                .sorted(Comparator.comparing(PricingReplayDiff::path))
                .toList();
    }

    @Override
    public void saveEvent(PricingReplayEvent event) {
        events.add(event);
    }

    @Override
    public void saveAudit(PricingReplayAudit audit) {
        audits.add(audit);
    }

    public List<PricingReplayEvent> events() {
        return List.copyOf(events);
    }

    public List<PricingReplayAudit> audits() {
        return List.copyOf(audits);
    }

    public List<PricingReplayDiff> diffs() {
        return List.copyOf(diffs);
    }

    private static String snapshotKey(String tenantId, ReplaySourceType sourceType, String sourceId) {
        return tenantId + ":" + sourceType + ":" + sourceId;
    }
}
