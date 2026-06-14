package com.wcpe.tenantcontext;

import com.wcpe.tenantcontext.TenantRegistrationService.TenantCreateRequest;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantDetails;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantFeatureFlags;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantFilter;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantRegistrationException;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantUpdateRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tenants")
public class TenantAdminController {
    private final TenantRegistrationService tenantService;

    public TenantAdminController(TenantRegistrationService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantDetails> createTenant(
        @RequestBody TenantCreateRequest request,
        @RequestHeader(name = "X-Actor-Id", required = false) String actorId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request, actorId));
    }

    @GetMapping
    public TenantListResponse listTenants(
        @RequestParam(name = "search", required = false) String search,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        List<TenantDetails> filtered = tenantService.listTenants(new TenantFilter(search, status));
        int safeSize = Math.max(1, size);
        int safePage = Math.max(0, page);
        int fromIndex = Math.min(filtered.size(), safePage * safeSize);
        int toIndex = Math.min(filtered.size(), fromIndex + safeSize);
        List<TenantDetails> content = filtered.subList(fromIndex, toIndex);
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new TenantListResponse(content, filtered.size(), totalPages, safePage, safeSize);
    }

    @GetMapping("/{tenantId}")
    public TenantDetails getTenant(@PathVariable String tenantId) {
        return tenantService.read(tenantId);
    }

    @PatchMapping("/{tenantId}")
    public TenantDetails updateTenant(
        @PathVariable String tenantId,
        @RequestBody TenantUpdateRequest request,
        @RequestHeader(name = "X-Actor-Id", required = false) String actorId
    ) {
        return tenantService.updateTenant(tenantId, request, actorId);
    }

    @PostMapping("/{tenantId}/activate")
    public TenantDetails activateTenant(@PathVariable String tenantId) {
        return tenantService.activate(tenantId);
    }

    @PostMapping("/{tenantId}/suspend")
    public TenantDetails suspendTenant(@PathVariable String tenantId) {
        return tenantService.suspend(tenantId);
    }

    @PostMapping("/{tenantId}/deactivate")
    public TenantDetails deactivateTenant(@PathVariable String tenantId) {
        return tenantService.deactivate(tenantId);
    }

    @GetMapping("/{tenantId}/feature-flags")
    public TenantFeatureFlags getFeatureFlags(@PathVariable String tenantId) {
        return tenantService.getFeatureFlags(tenantId);
    }

    @PatchMapping("/{tenantId}/feature-flags")
    public TenantFeatureFlags updateFeatureFlags(
        @PathVariable String tenantId,
        @RequestBody FeatureFlagsUpdateRequest request,
        @RequestHeader(name = "X-Actor-Id", required = false) String actorId
    ) {
        return tenantService.updateFeatureFlags(tenantId, request == null ? Map.of() : request.flags(), actorId);
    }

    @GetMapping("/{tenantId}/users/count")
    public UserCountResponse getUserCount(@PathVariable String tenantId) {
        return new UserCountResponse(tenantId, tenantService.getUserCount(tenantId));
    }

    @ExceptionHandler(TenantRegistrationException.class)
    public ResponseEntity<ErrorResponse> handleTenantError(TenantRegistrationException error) {
        return ResponseEntity.status(error.httpStatus()).body(new ErrorResponse(error.code(), error.getMessage(), error.existingTenantId(), error.fieldErrors()));
    }

    public record TenantListResponse(List<TenantDetails> content, long totalElements, int totalPages, int page, int size) { }

    public record FeatureFlagsUpdateRequest(Map<String, Boolean> flags) { }

    public record UserCountResponse(String tenantId, long userCount) { }

    public record ErrorResponse(String code, String message, String existingTenantId, Map<String, String> fieldErrors) { }
}
