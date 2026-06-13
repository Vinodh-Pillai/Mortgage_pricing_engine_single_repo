package com.wcpe.tenantcontext;

public class TenantCalendarConfigService {
    public TenantCalendarConfig currentCalendar(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenantId is required");
        }
        return TenantCalendarConfig.configuredDefault(tenantId.trim());
    }
}
