package com.wcpe.mladvisory;

import com.wcpe.mladvisory.FairLendingAnalysisService.FairLendingAnalysisRequest;
import com.wcpe.mladvisory.FairLendingAnalysisService.FairLendingReport;
import com.wcpe.mladvisory.FairLendingAnalysisService.PricingOutcome;
import com.wcpe.mladvisory.FairLendingAnalysisService.PricingOutcomeRecordedEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fair-lending")
public final class FairLendingController {
  private final FairLendingAnalysisService service;

  public FairLendingController() {
    this(new FairLendingAnalysisService());
  }

  FairLendingController(FairLendingAnalysisService service) {
    this.service = service;
  }

  @PostMapping("/pricing-outcomes")
  public PricingOutcome recordOutcome(@RequestBody PricingOutcomeRecordedEvent event) {
    return service.recordPricingOutcome(event);
  }

  @PostMapping("/analyze")
  public FairLendingReport analyze(@RequestBody FairLendingAnalysisRequest request) {
    return service.analyze(request);
  }

  @GetMapping("/report/{reportId}")
  public ResponseEntity<FairLendingReport> report(@PathVariable UUID reportId) {
    return service.report(reportId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }

  @GetMapping("/violations")
  public List<FairLendingAnalysisService.FairLendingViolation> violations(@RequestParam(required = false) String tenantId) {
    return service.violations(tenantId);
  }
}
