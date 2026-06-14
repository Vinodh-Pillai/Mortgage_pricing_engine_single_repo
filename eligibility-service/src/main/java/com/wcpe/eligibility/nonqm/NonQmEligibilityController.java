package com.wcpe.eligibility.nonqm;

import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.NonQmEligibilityRequest;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.NonQmEligibilityResult;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.NonQmEligibilityRuleSet;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.PpeRuleSetExportResponse;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.PpeRuleSetImportRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class NonQmEligibilityController {
    private final NonQmEligibilityService service;

    public NonQmEligibilityController(NonQmEligibilityService service) {
        this.service = service;
    }

    @PostMapping({"/api/v1/eligibility/non-qm/evaluate", "/api/v1/tenants/{tenantId}/eligibility/non-qm/evaluate"})
    ResponseEntity<NonQmEligibilityResult> evaluate(
        @PathVariable(required = false) UUID tenantId,
        @RequestBody NonQmEligibilityRequest request
    ) {
        return ResponseEntity.ok(service.evaluate(request));
    }

    @PostMapping({"/api/v1/eligibility/non-qm/rule-sets/import", "/api/v1/tenants/{tenantId}/eligibility/non-qm/rule-sets/import"})
    ResponseEntity<NonQmEligibilityRuleSet> importRules(
        @PathVariable(required = false) UUID tenantId,
        @RequestBody PpeRuleSetImportRequest request
    ) {
        return ResponseEntity.ok(service.importRuleSet(request));
    }

    @GetMapping({"/api/v1/eligibility/non-qm/rule-sets/{ruleSetId}/export", "/api/v1/tenants/{tenantId}/eligibility/non-qm/rule-sets/{ruleSetId}/export"})
    ResponseEntity<PpeRuleSetExportResponse> exportRules(
        @PathVariable(required = false) UUID tenantId,
        @PathVariable String ruleSetId,
        @RequestParam(defaultValue = "OPTIMAL_BLUE") String format
    ) {
        return ResponseEntity.ok(service.exportRuleSet(ruleSetId, format));
    }
}
