package com.wcpe.catalog.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/authorizations")
public class TenantProductAuthorizationAdminController {
    private final TenantProductAuthorizationService authorizationService;
    private final AuthorizationService rbac;

    public TenantProductAuthorizationAdminController(TenantProductAuthorizationService authorizationService, AuthorizationService rbac) {
        this.authorizationService = authorizationService;
        this.rbac = rbac;
    }

    @GetMapping
    public List<TenantProductAuthorization> list(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) String productCode,
        @RequestParam(required = false) String status,
        HttpServletRequest request
    ) {
        rbac.authorize("READ_CATALOG", request.getHeader("X-Roles"));
        return authorizationService.list(tenantId, productCode, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantProductAuthorization create(@RequestBody TenantProductAuthorizationCommand command, HttpServletRequest request) {
        rbac.authorize("WRITE_CATALOG", request.getHeader("X-Roles"));
        return authorizationService.save(command, actor(request));
    }

    @PatchMapping("/{tenantId}/{productCode}")
    public TenantProductAuthorization update(
        @PathVariable UUID tenantId,
        @PathVariable String productCode,
        @RequestParam(required = false) String investorCode,
        @RequestParam(required = false) String channelCode,
        @RequestBody TenantProductAuthorizationPatch patch,
        HttpServletRequest request
    ) {
        rbac.authorize("WRITE_CATALOG", request.getHeader("X-Roles"));
        return authorizationService.update(tenantId, productCode, investorCode, channelCode, patch);
    }

    private static String actor(HttpServletRequest request) {
        String actor = request.getHeader("X-Actor-Id");
        return actor == null || actor.isBlank() ? "catalog-admin" : actor;
    }
}
