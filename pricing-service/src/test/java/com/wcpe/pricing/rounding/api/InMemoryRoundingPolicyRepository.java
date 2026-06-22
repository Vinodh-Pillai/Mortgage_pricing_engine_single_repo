package com.wcpe.pricing.rounding.api;

import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyRepository;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyStatus;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyVersion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRoundingPolicyRepository implements RoundingPolicyRepository {
    private final Map<String, RoundingPolicyVersion> policies = new ConcurrentHashMap<>();

    @Override
    public void save(RoundingPolicyVersion policy) {
        policies.put(policy.id(), policy);
    }

    @Override
    public Optional<RoundingPolicyVersion> findById(String policyVersionId) {
        return Optional.ofNullable(policies.get(policyVersionId));
    }

    @Override
    public List<RoundingPolicyVersion> findPublishedForScope(
            String tenantId,
            String scope,
            String productCode,
            String investorCode,
            String channelCode) {
        return policies.values().stream()
                .filter(policy -> policy.status() == RoundingPolicyStatus.PUBLISHED)
                .filter(policy -> tenantId.equals(policy.tenantId()))
                .filter(policy -> Objects.equals(scope, policy.scope()))
                .filter(policy -> Objects.equals(productCode, policy.productCode()))
                .filter(policy -> Objects.equals(investorCode, policy.investorCode()))
                .filter(policy -> Objects.equals(channelCode, policy.channelCode()))
                .toList();
    }
}
