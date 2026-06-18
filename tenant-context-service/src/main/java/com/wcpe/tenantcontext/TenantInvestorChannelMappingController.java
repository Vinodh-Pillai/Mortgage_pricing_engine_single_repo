package com.wcpe.tenantcontext;

import com.wcpe.tenantcontext.TenantInvestorChannelMappingService.FieldError;
import com.wcpe.tenantcontext.TenantInvestorChannelMappingService.TenantInvestorChannelContext;
import com.wcpe.tenantcontext.TenantInvestorChannelMappingService.TenantInvestorChannelMapping;
import com.wcpe.tenantcontext.TenantInvestorChannelMappingService.TenantInvestorChannelMappingRequest;
import com.wcpe.tenantcontext.TenantInvestorChannelMappingService.TenantMappingException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant-mappings")
public class TenantInvestorChannelMappingController {
    private final TenantInvestorChannelMappingService service;

    public TenantInvestorChannelMappingController(TenantInvestorChannelMappingService service) {
        this.service = service;
    }

    @PostMapping("/resolve")
    public TenantInvestorChannelContext resolve(@RequestBody TenantInvestorChannelMappingRequest request) {
        return service.resolve(request);
    }

    @PostMapping
    public TenantInvestorChannelMapping save(@RequestBody TenantInvestorChannelMapping mapping) {
        return service.save(mapping);
    }

    @GetMapping("/tenants/{tenantId}")
    public List<TenantInvestorChannelMapping> list(@PathVariable String tenantId) {
        return service.list(tenantId);
    }

    @ExceptionHandler(TenantMappingException.class)
    ResponseEntity<Map<String, Object>> handleMappingError(TenantMappingException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "errorCode", error.code(),
            "code", error.code(),
            "message", error.getMessage(),
            "fieldErrors", error.fieldErrors().stream().map(TenantInvestorChannelMappingController::fieldErrorBody).toList()
        ));
    }

    private static Map<String, String> fieldErrorBody(FieldError fieldError) {
        return Map.of(
            "field", fieldError.field(),
            "code", fieldError.code(),
            "message", fieldError.message()
        );
    }
}
