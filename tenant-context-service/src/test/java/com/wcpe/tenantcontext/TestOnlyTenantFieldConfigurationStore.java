package com.wcpe.tenantcontext;

import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfiguration;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigurationAuditRecord;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigurationDraft;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigurationStore;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigurationVersion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class TestOnlyTenantFieldConfigurationStore implements TenantFieldConfigurationStore {
    private final LinkedHashMap<String, TenantFieldConfiguration> configurations = new LinkedHashMap<>();
    private final LinkedHashMap<String, TenantFieldConfigurationDraft> drafts = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<TenantFieldConfigurationVersion>> versions = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> currentVersions = new LinkedHashMap<>();
    private final List<TenantFieldConfigurationAuditRecord> auditRecords = new ArrayList<>();

    @Override
    public void saveConfiguration(TenantFieldConfiguration configuration) {
        configurations.put(fieldKey(configuration.tenantId(), configuration.surface(), configuration.fieldId()), configuration);
    }

    @Override
    public void removeConfigurationsForTenantSurface(String tenantId, String surface) {
        String prefix = surfaceKey(tenantId, surface) + "|";
        configurations.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public List<TenantFieldConfiguration> configurationsForTenantSurface(String tenantId, String surface) {
        String prefix = surfaceKey(tenantId, surface) + "|";
        return configurations.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .map(java.util.Map.Entry::getValue)
            .sorted(Comparator.comparing(TenantFieldConfiguration::fieldId))
            .toList();
    }

    @Override
    public Optional<TenantFieldConfiguration> configuration(String tenantId, String surface, String fieldId) {
        return Optional.ofNullable(configurations.get(fieldKey(tenantId, surface, fieldId)));
    }

    @Override
    public void saveDraft(String tenantId, String surface, TenantFieldConfigurationDraft draft) {
        drafts.put(surfaceKey(tenantId, surface), draft);
    }

    @Override
    public Optional<TenantFieldConfigurationDraft> draftForTenantSurface(String tenantId, String surface) {
        return Optional.ofNullable(drafts.get(surfaceKey(tenantId, surface)));
    }

    @Override
    public void removeDraft(String tenantId, String surface) {
        drafts.remove(surfaceKey(tenantId, surface));
    }

    @Override
    public int currentVersion(String tenantId, String surface) {
        return currentVersions.getOrDefault(surfaceKey(tenantId, surface), 0);
    }

    @Override
    public void saveCurrentVersion(String tenantId, String surface, int version) {
        currentVersions.put(surfaceKey(tenantId, surface), version);
    }

    @Override
    public void appendVersion(String tenantId, String surface, TenantFieldConfigurationVersion version) {
        versions.computeIfAbsent(surfaceKey(tenantId, surface), ignored -> new ArrayList<>()).add(version);
    }

    @Override
    public List<TenantFieldConfigurationVersion> versionsForTenantSurface(String tenantId, String surface) {
        return List.copyOf(versions.getOrDefault(surfaceKey(tenantId, surface), List.of()));
    }

    @Override
    public void appendAuditRecord(TenantFieldConfigurationAuditRecord auditRecord) {
        auditRecords.add(auditRecord);
    }

    @Override
    public List<TenantFieldConfigurationAuditRecord> auditRecordsForTenant(String tenantId) {
        String tenantKey = normalize(tenantId);
        return auditRecords.stream()
            .filter(record -> normalize(record.tenantId()).equals(tenantKey))
            .sorted(Comparator.comparing(TenantFieldConfigurationAuditRecord::timestamp))
            .toList();
    }

    private static String fieldKey(String tenantId, String surface, String fieldId) {
        return surfaceKey(tenantId, surface) + "|" + normalize(fieldId);
    }

    private static String surfaceKey(String tenantId, String surface) {
        return normalize(tenantId) + "|" + normalize(surface);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
