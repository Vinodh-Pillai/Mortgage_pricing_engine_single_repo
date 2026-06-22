package com.wcpe.tenantcontext;

import com.wcpe.tenantcontext.TenantRegistrationService.FeatureFlag;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantRecord;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantRegistrationStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class TestOnlyTenantRegistrationStore implements TenantRegistrationStore {
    private final LinkedHashMap<String, TenantRecord> tenantsById = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> tenantIdByNormalizedName = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, FeatureFlag>> featureFlagsByTenantId = new LinkedHashMap<>();

    @Override
    public Optional<TenantRecord> tenantById(String tenantId) {
        return Optional.ofNullable(tenantsById.get(required(tenantId)));
    }

    @Override
    public Optional<String> tenantIdByNormalizedName(String normalizedName) {
        return Optional.ofNullable(tenantIdByNormalizedName.get(required(normalizedName)));
    }

    @Override
    public List<TenantRecord> tenants() {
        return List.copyOf(tenantsById.values());
    }

    @Override
    public void saveTenant(String normalizedName, TenantRecord tenant) {
        tenantsById.put(required(tenant.tenantId()), tenant);
        tenantIdByNormalizedName.put(required(normalizedName), tenant.tenantId());
    }

    @Override
    public Optional<LinkedHashMap<String, FeatureFlag>> featureFlagsByTenantId(String tenantId) {
        LinkedHashMap<String, FeatureFlag> flags = featureFlagsByTenantId.get(required(tenantId));
        return flags == null ? Optional.empty() : Optional.of(new LinkedHashMap<>(flags));
    }

    @Override
    public void saveFeatureFlags(String tenantId, LinkedHashMap<String, FeatureFlag> flags) {
        featureFlagsByTenantId.put(required(tenantId), new LinkedHashMap<>(flags));
    }

    @Override
    public int tenantCount() {
        return tenantsById.size();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("key is required");
        return value.trim();
    }
}
