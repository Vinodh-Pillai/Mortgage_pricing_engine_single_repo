package com.wcpe.tenantcontext;

import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantPipelineAccessAuditRecord;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantPipelineConfiguration;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantPipelineEligibilityStore;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.UserTenantAssignment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class TestOnlyTenantPipelineEligibilityStore implements TenantPipelineEligibilityStore {
    private final LinkedHashMap<String, TenantPipelineConfiguration> configurationsByTenant = new LinkedHashMap<>();
    private final LinkedHashMap<String, UserTenantAssignment> userAssignments = new LinkedHashMap<>();
    private final List<TenantPipelineAccessAuditRecord> accessAuditRecords = new ArrayList<>();

    @Override
    public void saveConfiguration(TenantPipelineConfiguration configuration) {
        configurationsByTenant.put(key(configuration.tenantId()), configuration);
    }

    @Override
    public Optional<TenantPipelineConfiguration> configurationForTenant(String tenantId) {
        return Optional.ofNullable(configurationsByTenant.get(key(tenantId)));
    }

    @Override
    public Optional<UserTenantAssignment> assignmentForUser(String userId) {
        return Optional.ofNullable(userAssignments.get(key(userId)));
    }

    @Override
    public void saveAssignment(UserTenantAssignment assignment) {
        userAssignments.put(key(assignment.userId()), assignment);
    }

    @Override
    public void appendAccessAuditRecord(TenantPipelineAccessAuditRecord record) {
        accessAuditRecords.add(record);
    }

    @Override
    public List<TenantPipelineAccessAuditRecord> accessAuditRecordsForTenant(String tenantId) {
        String tenantKey = key(tenantId);
        return accessAuditRecords.stream()
            .filter(record -> key(record.tenantId()).equals(tenantKey))
            .toList();
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
