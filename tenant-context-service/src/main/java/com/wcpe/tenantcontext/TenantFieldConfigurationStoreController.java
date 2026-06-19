package com.wcpe.tenantcontext;

import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.FieldError;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigException;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfiguration;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigurationAuditRecord;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigurationDraft;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigurationVersion;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant-field-configurations")
public class TenantFieldConfigurationStoreController {
    private final TenantFieldConfigurationStoreService service;
    private final TenantContextService tenantContextService;

    public TenantFieldConfigurationStoreController(TenantFieldConfigurationStoreService service, TenantContextService tenantContextService) {
        this.service = service;
        this.tenantContextService = tenantContextService;
    }

    @PostMapping("/tenants/{tenantId}/fields")
    public TenantFieldConfiguration save(@PathVariable String tenantId, @RequestHeader Map<String, String> headers, @RequestBody TenantFieldConfiguration request) {
        requireTenantContext(tenantId, headers);
        return service.save(withTenant(tenantId, request));
    }

    @PutMapping("/tenants/{tenantId}/surfaces/{surface}/fields")
    public List<TenantFieldConfiguration> replaceTenantSurface(@PathVariable String tenantId, @PathVariable String surface, @RequestHeader Map<String, String> headers, @RequestBody List<TenantFieldConfiguration> requests) {
        requireTenantContext(tenantId, headers);
        return service.replaceTenantSurface(tenantId, surface, requests == null ? List.of() : requests.stream().map(request -> withTenantAndSurface(tenantId, surface, request)).toList());
    }

    @GetMapping("/tenants/{tenantId}/surfaces/{surface}/fields")
    public List<TenantFieldConfiguration> activeForTenantSurface(@PathVariable String tenantId, @PathVariable String surface, @RequestHeader Map<String, String> headers) {
        requireTenantContext(tenantId, headers);
        return service.activeForTenantSurface(tenantId, surface);
    }

    @PostMapping("/tenants/{tenantId}/surfaces/{surface}/draft")
    public TenantFieldConfigurationDraft saveDraft(@PathVariable String tenantId, @PathVariable String surface, @RequestHeader Map<String, String> headers, @RequestBody TenantFieldDraftRequest request) {
        TenantContext context = requireTenantContext(tenantId, headers);
        TenantFieldDraftRequest safeRequest = request == null ? TenantFieldDraftRequest.empty() : request;
        return service.saveDraft(tenantId, surface, safeRequest.configurations(), safeRequest.conditionFieldRefs(), userId(safeRequest.userId(), context));
    }

    @PostMapping("/tenants/{tenantId}/surfaces/{surface}/publish")
    public TenantFieldConfigurationVersion publishDraft(@PathVariable String tenantId, @PathVariable String surface, @RequestHeader Map<String, String> headers, @RequestBody TenantFieldLifecycleRequest request) {
        TenantContext context = requireTenantContext(tenantId, headers);
        return service.publishDraft(tenantId, surface, userId(request == null ? null : request.userId(), context));
    }

    @PostMapping("/tenants/{tenantId}/surfaces/{surface}/rollback")
    public TenantFieldConfigurationVersion rollback(@PathVariable String tenantId, @PathVariable String surface, @RequestHeader Map<String, String> headers, @RequestBody TenantFieldLifecycleRequest request) {
        TenantContext context = requireTenantContext(tenantId, headers);
        return service.rollbackToPreviousVersion(tenantId, surface, userId(request == null ? null : request.userId(), context));
    }

    @GetMapping("/tenants/{tenantId}/surfaces/{surface}/versions")
    public List<TenantFieldConfigurationVersion> publishedVersions(@PathVariable String tenantId, @PathVariable String surface, @RequestHeader Map<String, String> headers) {
        requireTenantContext(tenantId, headers);
        return service.publishedVersions(tenantId, surface);
    }

    @GetMapping("/tenants/{tenantId}/audit")
    public List<TenantFieldConfigurationAuditRecord> auditRecords(@PathVariable String tenantId, @RequestHeader Map<String, String> headers) {
        requireTenantContext(tenantId, headers);
        return service.auditRecordsForTenant(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/surfaces/{surface}/fields/{fieldId}")
    public ResponseEntity<TenantFieldConfiguration> activeField(@PathVariable String tenantId, @PathVariable String surface, @PathVariable String fieldId, @RequestHeader Map<String, String> headers) {
        requireTenantContext(tenantId, headers);
        return service.activeField(tenantId, surface, fieldId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(TenantFieldConfigException.class)
    ResponseEntity<Map<String, Object>> handleTenantFieldConfigError(TenantFieldConfigException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "errorCode", error.code(),
            "code", error.code(),
            "message", error.getMessage(),
            "fieldErrors", error.fieldErrors().stream().map(TenantFieldConfigurationStoreController::fieldErrorBody).toList()
        ));
    }

    @ExceptionHandler(TenantContextValidationException.class)
    ResponseEntity<Map<String, Object>> handleTenantContextError(TenantContextValidationException error) {
        HttpStatus status = "TENANT_CONTEXT_MISSING".equals(error.code()) || "TENANT_CONTEXT_MALFORMED".equals(error.code())
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(Map.of(
            "errorCode", error.code(),
            "code", error.code(),
            "message", error.getMessage()
        ));
    }

    private TenantContext requireTenantContext(String pathTenantId, Map<String, String> headers) {
        return tenantContextService.resolve(pathTenantId, new TenantContextInput(
            header(headers, "X-Tenant-Id"),
            header(headers, "X-Request-Id"),
            header(headers, "X-Trace-Id"),
            header(headers, "X-Actor-Id"),
            header(headers, "X-Actor-Type"),
            csv(headers, "X-Roles"),
            csv(headers, "X-Scopes"),
            header(headers, "X-Channel"),
            header(headers, "X-Correlation-Id"),
            header(headers, "X-Causation-Id"),
            header(headers, "X-Idempotency-Key"),
            header(headers, "X-Request-Source"),
            csv(headers, "X-Allowed-Tenant-Ids"),
            header(headers, "X-Selected-Tenant-Id"),
            header(headers, "X-Tenant-Status")
        ));
    }

    private static List<String> csv(Map<String, String> headers, String name) {
        String value = header(headers, name);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .toList();
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String direct = headers.get(name);
        if (direct != null) {
            return direct;
        }
        return Optional.ofNullable(headers.get(name.toLowerCase())).orElseGet(() -> headers.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null));
    }

    private static TenantFieldConfiguration withTenant(String tenantId, TenantFieldConfiguration request) {
        if (request == null) {
            throw new TenantFieldConfigException("TENANT_FIELD_CONFIG_REQUIRED", List.of(new FieldError("tenantFieldConfiguration", "TENANT_FIELD_CONFIG_REQUIRED", "Tenant field configuration is required.")));
        }
        return new TenantFieldConfiguration(request.configurationId(), tenantId, request.surface(), request.fieldId(), request.origin(), request.systemFieldRef(), request.nameAlias(), request.descriptionAlias(), request.enabled(), request.omitted(), request.updatedAt(), request.auditRef());
    }

    private static TenantFieldConfiguration withTenantAndSurface(String tenantId, String surface, TenantFieldConfiguration request) {
        TenantFieldConfiguration tenantScoped = withTenant(tenantId, request);
        return new TenantFieldConfiguration(tenantScoped.configurationId(), tenantId, surface, tenantScoped.fieldId(), tenantScoped.origin(), tenantScoped.systemFieldRef(), tenantScoped.nameAlias(), tenantScoped.descriptionAlias(), tenantScoped.enabled(), tenantScoped.omitted(), tenantScoped.updatedAt(), tenantScoped.auditRef());
    }

    private static String userId(String requestedUserId, TenantContext context) {
        if (requestedUserId != null && !requestedUserId.isBlank()) {
            return requestedUserId.trim();
        }
        return context.actor().actorId();
    }

    private static Map<String, String> fieldErrorBody(FieldError fieldError) {
        return Map.of(
            "field", fieldError.field(),
            "code", fieldError.code(),
            "message", fieldError.message()
        );
    }

    public record TenantFieldDraftRequest(List<TenantFieldConfiguration> configurations, Map<String, List<String>> conditionFieldRefs, String userId) {
        static TenantFieldDraftRequest empty() {
            return new TenantFieldDraftRequest(List.of(), Map.of(), null);
        }
    }

    public record TenantFieldLifecycleRequest(String userId) { }
}
