package com.wcpe.pricing.parrate;

import com.wcpe.pricing.parrate.ParRateIdentificationApi.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryParRateIdentificationRepository implements ParRateIdentificationRepository {
    private final List<ParPolicyVersion> policies = new ArrayList<>();
    private final Map<UUID, ParRateIdentificationResult> results = new ConcurrentHashMap<>();
    private final List<ParRateEvent> events = new ArrayList<>();
    private final List<ParRateAudit> audits = new ArrayList<>();
    private final Set<String> invalidatedPolicyCacheKeys = ConcurrentHashMap.newKeySet();

    public void addPolicy(ParPolicyVersion policy) {
        policies.add(Objects.requireNonNull(policy));
    }

    @Override
    public Optional<ParPolicyVersion> findPublishedPolicy(String tenantId, String productCode, String investorCode,
            String channelCode, String policyVersionId, Instant asOf) {
        List<ParPolicyVersion> matching = policies.stream()
                .filter(policy -> policy.matches(tenantId, productCode, investorCode, channelCode, policyVersionId, asOf))
                .toList();
        if (matching.size() > 1) {
            throw new ParRateIdentificationException(ParRateErrorCode.PAR_POLICY_MISSING,
                    "ambiguous published par policy versions");
        }
        return matching.stream().findFirst();
    }

    @Override
    public void save(ParRateIdentificationResult result) {
        results.put(result.id(), result);
    }

    @Override
    public Optional<ParRateIdentificationResult> findById(UUID id) {
        return Optional.ofNullable(results.get(id));
    }

    @Override
    public void saveEvent(ParRateEvent event) {
        events.add(event);
    }

    @Override
    public void saveAudit(ParRateAudit audit) {
        audits.add(audit);
    }

    @Override
    public void invalidatePolicyCache(String cacheKey) {
        invalidatedPolicyCacheKeys.add(cacheKey);
    }

    public List<ParRateEvent> events() {
        return List.copyOf(events);
    }

    public List<ParRateAudit> audits() {
        return List.copyOf(audits);
    }

    public boolean wasPolicyCacheInvalidated(String cacheKey) {
        return invalidatedPolicyCacheKeys.contains(cacheKey);
    }
}
