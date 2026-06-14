package com.wcpe.migration.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/migration")
class PpeMigrationController {
    private final PpeMigrationService service;

    PpeMigrationController(PpeMigrationService service) {
        this.service = service;
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    PpeMigrationService.ImportResponse importPackage(@PathVariable UUID tenantId,
            @RequestBody PpeMigrationService.ImportRequest request) {
        return service.importPackage(tenantId, request);
    }

    @GetMapping("/imports/{importId}/validation-report")
    PpeMigrationService.ValidationReport validationReport(@PathVariable UUID tenantId, @PathVariable String importId) {
        return service.validationReport(tenantId, importId);
    }

    @PostMapping("/imports/{importId}/publish-request")
    PpeMigrationService.PublishRequestResult publishRequest(@PathVariable UUID tenantId, @PathVariable String importId) {
        return service.requestPublish(tenantId, importId);
    }

    @PostMapping("/exports")
    @ResponseStatus(HttpStatus.CREATED)
    PpeMigrationService.ExportResponse exportPackage(@PathVariable UUID tenantId,
            @RequestBody PpeMigrationService.ExportRequest request) {
        return service.exportPackage(tenantId, request);
    }

    @GetMapping("/audit")
    PpeMigrationService.AuditTrail audit(@PathVariable UUID tenantId) {
        return service.auditTrail(tenantId);
    }
}
