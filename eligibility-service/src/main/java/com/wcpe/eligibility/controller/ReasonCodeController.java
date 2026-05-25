package com.wcpe.eligibility.controller;

import com.wcpe.eligibility.domain.models.ReasonCode;
import com.wcpe.eligibility.service.ReasonCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReasonCodeController {
    private final ReasonCodeService reasonCodeService;

    public ReasonCodeController(ReasonCodeService reasonCodeService) {
        this.reasonCodeService = reasonCodeService;
    }

    @GetMapping("/reason-codes")
    public ResponseEntity<Map<String, Object>> getReasonCodes(@RequestParam(required = false) String ruleCode) {
        List<ReasonCode> codes;
        if (ruleCode != null && !ruleCode.isBlank()) {
            codes = reasonCodeService.getByRuleCode(ruleCode);
        } else {
            codes = reasonCodeService.getAll();
        }
        return ResponseEntity.ok(Map.of("reasonCodes", codes, "count", codes.size()));
    }
}
