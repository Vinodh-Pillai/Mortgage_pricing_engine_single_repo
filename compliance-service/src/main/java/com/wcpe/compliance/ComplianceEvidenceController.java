package com.wcpe.compliance;

import com.wcpe.compliance.ComplianceEvidenceRegistryService.ArtifactExportView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.ComplianceEvidenceRegistryView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.LegalHoldCommand;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.PrivacyProcessCommand;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.PrivacyRequestView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.RetentionDeleteCommand;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.RetentionDeletionGateView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.RetentionRuleView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.SecurityAcknowledgeCommand;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.SecurityEventView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ComplianceEvidenceController {
  private final ComplianceEvidenceRegistryService service;

  public ComplianceEvidenceController(ComplianceEvidenceRegistryService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/compliance/evidence")
  public ComplianceEvidenceRegistryView registry(
      @RequestParam(name = "redactionProfile", required = false) String redactionProfile) {
    return service.registry(redactionProfile);
  }

  @GetMapping("/api/v1/compliance/evidence/artifacts/{artifactId}/export")
  public ArtifactExportView exportArtifact(
      @PathVariable String artifactId,
      @RequestParam(name = "redactionProfile", defaultValue = "PARTIAL") String redactionProfile) {
    return service.exportArtifact(artifactId, redactionProfile);
  }

  @PostMapping("/api/v1/compliance/privacy/requests/{requestId}/process")
  public PrivacyRequestView processPrivacyRequest(
      @PathVariable String requestId, @RequestBody(required = false) PrivacyProcessCommand command) {
    return service.processPrivacyRequest(requestId, command);
  }

  @PostMapping("/api/v1/compliance/security/events/{eventId}/acknowledge")
  public SecurityEventView acknowledgeSecurityEvent(
      @PathVariable String eventId, @RequestBody(required = false) SecurityAcknowledgeCommand command) {
    return service.acknowledgeSecurityEvent(eventId, command);
  }

  @PostMapping("/api/v1/compliance/retention/rules/{ruleId}/legal-hold")
  public RetentionRuleView applyLegalHold(
      @PathVariable String ruleId, @RequestBody(required = false) LegalHoldCommand command) {
    return service.applyLegalHold(ruleId, command);
  }

  @PostMapping("/api/v1/compliance/retention/rules/{ruleId}/delete")
  public RetentionDeletionGateView requestDeletion(
      @PathVariable String ruleId, @RequestBody(required = false) RetentionDeleteCommand command) {
    return service.requestDeletion(ruleId, command);
  }
}
