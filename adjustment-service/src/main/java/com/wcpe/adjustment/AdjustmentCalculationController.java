package com.wcpe.adjustment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/adjustments")
public final class AdjustmentCalculationController {
    private final AdjustmentService adjustmentService;

    public AdjustmentCalculationController(AdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<AdjustmentCalculationResult> calculate(@RequestBody AdjustmentCalculationRequest request) {
        return ResponseEntity.ok(adjustmentService.calculate(request));
    }
}
