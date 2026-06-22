package com.wcpe.pricing.calculationtables;

import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationDataTableLookupException;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationDataTableLookupRepository;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationDataTableVersion;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupAuditEvent;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupVersionStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCalculationDataTableLookupRepository implements CalculationDataTableLookupRepository {
    private final Map<String, List<CalculationDataTableVersion>> versionsByTable = new ConcurrentHashMap<>();
    private final List<LookupAuditEvent> events = new ArrayList<>();

    @Override
    public synchronized int nextVersionNumber(String tenantId, String tableId) {
        return versionsByTable.getOrDefault(tableKey(tenantId, tableId), List.of()).size() + 1;
    }

    @Override
    public synchronized void saveVersion(CalculationDataTableVersion version) {
        versionsByTable.computeIfAbsent(tableKey(version.tenantId(), version.tableId()), ignored -> new ArrayList<>())
                .add(version);
    }

    @Override
    public synchronized void replaceVersion(CalculationDataTableVersion version) {
        List<CalculationDataTableVersion> versions = versionsByTable.get(tableKey(version.tenantId(), version.tableId()));
        if (versions == null) {
            throw new CalculationDataTableLookupException("LOOKUP_VERSION_NOT_FOUND");
        }
        for (int index = 0; index < versions.size(); index++) {
            if (versions.get(index).versionId().equals(version.versionId())) {
                versions.set(index, version);
                return;
            }
        }
        throw new CalculationDataTableLookupException("LOOKUP_VERSION_NOT_FOUND");
    }

    @Override
    public synchronized Optional<CalculationDataTableVersion> findVersion(String tenantId, UUID versionId) {
        return versionsByTable.values().stream()
                .flatMap(List::stream)
                .filter(version -> version.tenantId().equals(tenantId))
                .filter(version -> version.versionId().equals(versionId))
                .findFirst();
    }

    @Override
    public synchronized Optional<CalculationDataTableVersion> findLatestVersion(String tenantId, String tableId) {
        return versionsByTable.getOrDefault(tableKey(tenantId, tableId), List.of()).stream()
                .max(Comparator.comparing(CalculationDataTableVersion::versionNumber));
    }

    @Override
    public synchronized Optional<CalculationDataTableVersion> findLatestPublishedVersion(String tenantId, String tableId) {
        return versionsByTable.getOrDefault(tableKey(tenantId, tableId), List.of()).stream()
                .filter(version -> version.status() == LookupVersionStatus.PUBLISHED)
                .max(Comparator.comparing(CalculationDataTableVersion::versionNumber));
    }

    @Override
    public synchronized void saveEvent(LookupAuditEvent event) {
        events.add(event);
    }

    @Override
    public synchronized List<LookupAuditEvent> events() {
        return List.copyOf(events);
    }

    private static String tableKey(String tenantId, String tableId) {
        return tenantId + ":" + tableId;
    }
}
