package com.wcpe.eligibility.controller;

import com.wcpe.eligibility.service.ReplayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class ReplayController {
    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replay(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, Object> body) {
        String requestJson = (String) body.get("requestJson");
        int policyVersion = (int) body.getOrDefault("policyVersion", 1);
        int ruleSetVersion = (int) body.getOrDefault("ruleSetVersion", 3);
        Map<String, Object> result = replayService.replay(tenantId, requestJson, policyVersion, ruleSetVersion);
        return ResponseEntity.ok(result);
    }
}
