package com.wcpe.tenantcontext;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantCalendarController {
    public static final String GET_CURRENT_TENANT_PATH = "/api/tenant/current";

    private final TenantCalendarConfigService service;

    public TenantCalendarController() {
        this(new TenantCalendarConfigService());
    }

    TenantCalendarController(TenantCalendarConfigService service) {
        this.service = service;
    }

    @GetMapping(GET_CURRENT_TENANT_PATH)
    public TenantCalendarConfig currentTenant(@RequestHeader("X-Tenant-Id") String tenantId) {
        return service.currentCalendar(tenantId);
    }
}
