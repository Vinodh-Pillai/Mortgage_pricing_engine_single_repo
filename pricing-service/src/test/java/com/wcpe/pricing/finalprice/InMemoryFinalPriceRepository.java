package com.wcpe.pricing.finalprice;

import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceAudit;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceEvent;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceRepository;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryFinalPriceRepository implements FinalPriceRepository {
    private final Map<UUID, FinalPriceResult> results = new ConcurrentHashMap<>();
    private final Map<String, UUID> idempotencyIndex = new ConcurrentHashMap<>();
    private final List<FinalPriceEvent> events = new ArrayList<>();
    private final List<FinalPriceAudit> audits = new ArrayList<>();

    @Override
    public void save(FinalPriceResult result) {
        results.put(result.id(), result);
        idempotencyIndex.put(result.tenantId() + ":" + result.idempotencyKey(), result.id());
    }

    @Override
    public Optional<FinalPriceResult> findById(UUID finalPriceId) {
        return Optional.ofNullable(results.get(finalPriceId));
    }

    @Override
    public Optional<FinalPriceResult> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        UUID resultId = idempotencyIndex.get(tenantId + ":" + idempotencyKey);
        return resultId == null ? Optional.empty() : findById(resultId);
    }

    @Override
    public void saveEvent(FinalPriceEvent event) {
        events.add(event);
    }

    @Override
    public void saveAudit(FinalPriceAudit audit) {
        audits.add(audit);
    }

    public List<FinalPriceEvent> events() {
        return List.copyOf(events);
    }

    public List<FinalPriceAudit> audits() {
        return List.copyOf(audits);
    }
}
