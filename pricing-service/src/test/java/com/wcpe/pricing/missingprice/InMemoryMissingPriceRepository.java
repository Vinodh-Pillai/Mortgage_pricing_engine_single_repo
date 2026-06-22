package com.wcpe.pricing.missingprice;

import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMissingPriceRepository implements MissingPriceRepository {
    private final Map<UUID, MissingPriceIncident> incidents = new ConcurrentHashMap<>();
    private final List<MissingPriceRetry> retries = new ArrayList<>();
    private final List<MissingPriceOutboxEvent> events = new ArrayList<>();
    private final List<MissingPriceAuditRecord> audits = new ArrayList<>();
    private final Map<String, IdempotencyRecord> idempotency = new ConcurrentHashMap<>();
    private final Map<String, UUID> negativeCache = new ConcurrentHashMap<>();

    @Override
    public void saveIncident(MissingPriceIncident incident) {
        incidents.put(incident.id(), incident);
    }

    @Override
    public Optional<MissingPriceIncident> findIncident(String tenantId, UUID incidentId) {
        MissingPriceIncident incident = incidents.get(incidentId);
        if (incident == null || !tenantId.equals(incident.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(incident);
    }

    @Override
    public void saveRetry(MissingPriceRetry retry) {
        retries.add(retry);
    }

    @Override
    public List<MissingPriceRetry> findRetries(String tenantId, UUID incidentId) {
        return retries.stream()
                .filter(retry -> tenantId.equals(retry.tenantId()))
                .filter(retry -> incidentId.equals(retry.incidentId()))
                .toList();
    }

    @Override
    public void saveOutboxEvent(MissingPriceOutboxEvent event) {
        events.add(event);
    }

    @Override
    public void saveAudit(MissingPriceAuditRecord audit) {
        audits.add(audit);
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotencyRecord(String tenantId, String idempotencyKey) {
        return Optional.ofNullable(idempotency.get(tenantId + ":" + idempotencyKey));
    }

    @Override
    public void saveIdempotencyRecord(IdempotencyRecord record) {
        idempotency.put(record.tenantId() + ":" + record.idempotencyKey(), record);
    }

    @Override
    public void putNegativeCache(String cacheKey, UUID incidentId) {
        negativeCache.put(cacheKey, incidentId);
    }

    @Override
    public void invalidateNegativeCache(String tenantId, String gridVersionRef) {
        negativeCache.keySet().removeIf(key -> key.startsWith("pricing:missing-price:" + tenantId + ":")
                && key.contains(":" + gridVersionRef + ":"));
    }

    public List<MissingPriceOutboxEvent> events() {
        return List.copyOf(events);
    }

    public List<MissingPriceAuditRecord> audits() {
        return List.copyOf(audits);
    }

    public boolean hasNegativeCacheEntry(String tenantId, String gridVersionRef) {
        return negativeCache.keySet().stream()
                .anyMatch(key -> key.startsWith("pricing:missing-price:" + tenantId + ":")
                        && key.contains(":" + gridVersionRef + ":"));
    }
}
