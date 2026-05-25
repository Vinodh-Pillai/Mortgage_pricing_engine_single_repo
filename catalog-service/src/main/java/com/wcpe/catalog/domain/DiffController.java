package com.wcpe.catalog.domain;

import com.wcpe.catalog.auth.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/catalog/diff")
class DiffController {

    private final DiffService diffService;
    private final AuthorizationService authorizationService;

    DiffController(DiffService diffService, AuthorizationService authorizationService) {
        this.diffService = diffService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    DiffResponse computeDiff(@PathVariable UUID tenantId,
                             @RequestParam int version_a,
                             @RequestParam int version_b,
                             HttpServletRequest http) {
        Headers headers = extractHeaders(http);
        authorizationService.authorize("READ_CATALOG", headers.roles);
        return diffService.computeDiff(tenantId, version_a, version_b);
    }

    Headers extractHeaders(HttpServletRequest request) {
        return new Headers(request.getHeader("X-Roles"));
    }

    record Headers(String roles) {}
}
