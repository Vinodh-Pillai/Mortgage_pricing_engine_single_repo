package com.wcpe.pricing.baserate;

import com.wcpe.pricing.baserate.BaseRateSelectionApi.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBaseRateSelectionRepository implements BaseRateSelectionRepository {
    private final Map<UUID, BaseRateSelection> selections = new ConcurrentHashMap<>();
    private final List<BasePricingGridVersion> gridVersions = new ArrayList<>();
    private final List<BasePricingGridRow> gridRows = new ArrayList<>();
    private final List<BaseRateSelectionAudit> audits = new ArrayList<>();
    private final Map<UUID, BasePricingGridImport> gridImports = new ConcurrentHashMap<>();
    private final List<BaseGridEvent> gridEvents = new ArrayList<>();
    private final Set<String> invalidatedGridCacheKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, BaseRateSelectionIdempotencyRecord> idempotency = new ConcurrentHashMap<>();

    @Override
    public void save(BaseRateSelection selection) {
        selections.put(selection.selectionId(), selection);
    }

    @Override
    public List<BasePricingGridVersion> findGridVersion(String tenantId, String productCode,
            String investorCode, String channelCode) {
        return gridVersions.stream()
                .filter(v -> tenantId.equals(v.tenantId()))
                .filter(v -> Objects.equals(productCode, v.productCode()))
                .filter(v -> Objects.equals(investorCode, v.investorCode()))
                .filter(v -> Objects.equals(channelCode, v.channelCode()))
                .toList();
    }

    @Override
    public List<BasePricingGridRow> findGridRows(String tenantId, UUID gridVersionId, int lockPeriodDays) {
        return gridRows.stream()
                .filter(row -> tenantId.equals(row.tenantId()))
                .filter(row -> gridVersionId.equals(row.gridVersionId()))
                .filter(row -> row.lockPeriodDays() == lockPeriodDays)
                .toList();
    }

    @Override
    public List<BasePricingGridRow> findGridRows(String tenantId, UUID gridVersionId) {
        return gridRows.stream()
                .filter(row -> tenantId.equals(row.tenantId()))
                .filter(row -> gridVersionId.equals(row.gridVersionId()))
                .toList();
    }

    @Override
    public Optional<BasePricingGridVersion> findGridVersionById(String tenantId, UUID gridVersionId) {
        return gridVersions.stream()
                .filter(version -> tenantId.equals(version.tenantId()))
                .filter(version -> gridVersionId.equals(version.id()))
                .findFirst();
    }

    @Override
    public int nextGridVersionNumber(String tenantId, String productCode, String investorCode, String channelCode) {
        return findGridVersion(tenantId, productCode, investorCode, channelCode).stream()
                .mapToInt(BasePricingGridVersion::versionNumber)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public void replaceGridVersion(BasePricingGridVersion version) {
        gridVersions.removeIf(existing -> existing.id().equals(version.id()));
        gridVersions.add(version);
    }

    @Override
    public void saveGridImport(BasePricingGridImport gridImport) {
        gridImports.put(gridImport.gridVersionId(), gridImport);
    }

    @Override
    public Optional<BasePricingGridImport> findGridImport(String tenantId, UUID gridVersionId) {
        BasePricingGridImport gridImport = gridImports.get(gridVersionId);
        if (gridImport == null || !tenantId.equals(gridImport.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(gridImport);
    }

    @Override
    public void markGridImportStatus(String tenantId, UUID gridVersionId, GridImportStatus status,
            List<String> validationMessages) {
        findGridImport(tenantId, gridVersionId)
                .ifPresent(gridImport -> gridImports.put(gridVersionId, gridImport.withStatus(status, validationMessages)));
    }

    @Override
    public void saveGridEvent(BaseGridEvent event) {
        gridEvents.add(event);
    }

    @Override
    public void invalidateGridCache(String tenantId, UUID gridVersionId) {
        invalidatedGridCacheKeys.add(tenantId + ":" + gridVersionId);
    }

    @Override
    public void saveAudit(BaseRateSelectionAudit audit) {
        audits.add(audit);
    }

    @Override
    public Optional<BaseRateSelectionIdempotencyRecord> findIdempotencyRecord(String tenantId, String idempotencyKey) {
        return Optional.ofNullable(idempotency.get(tenantId + ":" + idempotencyKey));
    }

    @Override
    public void saveIdempotencyRecord(BaseRateSelectionIdempotencyRecord record) {
        idempotency.put(record.tenantId() + ":" + record.idempotencyKey(), record);
    }

    public void addGridVersion(BasePricingGridVersion version) {
        gridVersions.add(version);
    }

    public void addGridRow(BasePricingGridRow row) {
        gridRows.add(row);
    }

    public List<BaseGridEvent> gridEvents() {
        return List.copyOf(gridEvents);
    }

    public List<BaseRateSelectionAudit> audits() {
        return List.copyOf(audits);
    }

    public boolean wasGridCacheInvalidated(String tenantId, UUID gridVersionId) {
        return invalidatedGridCacheKeys.contains(tenantId + ":" + gridVersionId);
    }
}
