package com.wcpe.catalog.domain;

import com.wcpe.catalog.auth.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/products/overlays")
class OverlayController {

    private final OverlayService overlayService;
    private final AuthorizationService authorizationService;

    OverlayController(OverlayService overlayService, AuthorizationService authorizationService) {
        this.overlayService = overlayService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    OverlayCreateResponse createOverlay(@PathVariable UUID tenantId,
                                        @RequestBody OverlayCreateRequest request,
                                        HttpServletRequest http) {
        Headers headers = extractHeaders(http);
        authorizationService.authorize("MANAGE_OVERLAY", headers.roles);
        return overlayService.createOverlay(tenantId, request, headers.actorId);
    }

    @PostMapping("/resolve")
    OverlayResolveResponse resolveOverlay(@PathVariable UUID tenantId,
                                          @RequestBody OverlayResolveRequest request,
                                          HttpServletRequest http) {
        Headers headers = extractHeaders(http);
        authorizationService.authorize("READ_CATALOG", headers.roles);
        return overlayService.resolveOverlay(tenantId, request);
    }

    Headers extractHeaders(HttpServletRequest request) {
        return new Headers(
            request.getHeader("X-Actor-Id"),
            request.getHeader("X-Roles")
        );
    }

    record Headers(String actorId, String roles) {}
}
