package com.wcpe.ratefeed.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RequestContext;
import com.wcpe.ratefeed.role.RateFeedRoles;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/mapping-wizard")
public class MappingWizardController {
  private final MappingWizardService service;

  public MappingWizardController(MappingWizardService service) {
    this.service = service;
  }

  @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<MappingWizardModels.MappingProposal> analyze(@PathVariable UUID tenantId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(defaultValue = "LLM") MappingWizardModels.AnalysisMode mode,
      HttpServletRequest request) throws IOException {
    return withRole(request, RateFeedRoles.RATE_FEED_NORMALIZE,
        () -> ResponseEntity.ok(uncheckedAnalyze(file, mode)));
  }

  @PostMapping("/propose")
  ResponseEntity<MappingWizardModels.MappingProposal> propose(@PathVariable UUID tenantId,
      @RequestBody MappingWizardModels.ProposeRequest body,
      HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_NORMALIZE, () -> ResponseEntity.ok(service.propose(tenantId, body)));
  }

  @PostMapping("/preview")
  ResponseEntity<MappingWizardModels.PreviewResponse> preview(@PathVariable UUID tenantId,
      @RequestBody MappingWizardModels.PreviewRequest body,
      HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_NORMALIZE, () -> ResponseEntity.ok(service.preview(body)));
  }

  @PostMapping("/profiles")
  ResponseEntity<MappingWizardModels.ProfileResponse> createProfile(@PathVariable UUID tenantId,
      @RequestBody MappingWizardModels.CreateProfileRequest body,
      HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_NORMALIZE,
        () -> ResponseEntity.status(HttpStatus.CREATED).body(service.createProfile(tenantId, body, actor(request))));
  }

  @GetMapping("/profiles")
  ResponseEntity<MappingWizardModels.ProfileListResponse> listProfiles(@PathVariable UUID tenantId, HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_VIEW, () -> ResponseEntity.ok(service.listProfiles(tenantId)));
  }

  @GetMapping("/profiles/{id}")
  ResponseEntity<MappingWizardModels.ProfileResponse> profile(@PathVariable UUID tenantId, @PathVariable UUID id, HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_VIEW, () -> ResponseEntity.ok(service.getProfile(tenantId, id)));
  }

  @PatchMapping("/profiles/{id}")
  ResponseEntity<MappingWizardModels.ProfileResponse> updateProfile(@PathVariable UUID tenantId, @PathVariable UUID id,
      @RequestBody MappingWizardModels.UpdateProfileRequest body,
      HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_NORMALIZE, () -> ResponseEntity.ok(service.updateDraft(tenantId, id, body, actor(request))));
  }

  @PostMapping("/profiles/{id}/simulate")
  ResponseEntity<MappingWizardModels.GovernanceResponse> simulate(@PathVariable UUID tenantId, @PathVariable UUID id, HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_NORMALIZE, () -> ResponseEntity.ok(service.simulate(tenantId, id)));
  }

  @PostMapping("/profiles/{id}/approve")
  ResponseEntity<MappingWizardModels.GovernanceResponse> approve(@PathVariable UUID tenantId, @PathVariable UUID id, HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_APPROVER, () -> ResponseEntity.ok(service.approve(tenantId, id, actor(request))));
  }

  @PostMapping("/profiles/{id}/publish")
  ResponseEntity<MappingWizardModels.GovernanceResponse> publish(@PathVariable UUID tenantId, @PathVariable UUID id, HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_ACTIVATE, () -> ResponseEntity.ok(service.publish(tenantId, id)));
  }

  @PostMapping("/profiles/{id}/new-version")
  ResponseEntity<MappingWizardModels.ProfileResponse> newVersion(@PathVariable UUID tenantId, @PathVariable UUID id,
      @RequestBody MappingWizardModels.UpdateProfileRequest body,
      HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_NORMALIZE, () -> ResponseEntity.status(HttpStatus.CREATED).body(service.newVersion(tenantId, id, body, actor(request))));
  }

  @PostMapping("/profiles/auto-match")
  ResponseEntity<MappingWizardModels.AutoMatchResponse> autoMatch(@PathVariable UUID tenantId, @RequestBody JsonNode fingerprint, HttpServletRequest request) {
    return withRole(request, RateFeedRoles.RATE_FEED_VIEW, () -> ResponseEntity.ok(service.autoMatch(tenantId, fingerprint)));
  }

  @ExceptionHandler(RateFeedException.class)
  ResponseEntity<Map<String, Object>> error(RateFeedException ex, HttpServletRequest request) {
    String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id")).filter(v -> !v.isBlank()).orElse(UUID.randomUUID().toString());
    return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage(), "correlationId", correlationId));
  }

  private MappingWizardModels.MappingProposal uncheckedAnalyze(MultipartFile file, MappingWizardModels.AnalysisMode mode) {
    try {
      return service.analyze(file.getOriginalFilename(), file.getBytes(), mode);
    } catch (IOException e) {
      throw new RateFeedException(HttpStatus.BAD_REQUEST, "MAPPING_ANALYSIS_FAILED", "Unable to analyze uploaded rate sheet.");
    }
  }

  private <T> T withRole(HttpServletRequest request, String role, Supplier<T> supplier) {
    try {
      RequestContext.roles(request.getHeader("X-Roles"));
      RateFeedRoles.require(role);
      return supplier.get();
    } finally {
      RequestContext.clear();
    }
  }

  private String actor(HttpServletRequest request) {
    return Optional.ofNullable(request.getHeader("X-Actor-Id")).filter(v -> !v.isBlank()).orElse("mapping-wizard-admin");
  }
}
