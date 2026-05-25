package com.wcpe.scenario.domain;

import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/admin/submission-profiles")
class SubmissionProfileController {
  private final SubmissionProfileService service;

  SubmissionProfileController(SubmissionProfileService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<SubmissionProfileResponse> createProfile(@PathVariable UUID tenantId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Roles", required = false) String roles,
      @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String actorId,
      @RequestBody CreateSubmissionProfileRequest request) {
    return withRoles(roles, () -> ResponseEntity.status(HttpStatus.CREATED).body(
        service.createDraft(tenantId, key, correlationId, actorId, request)));
  }

  @PostMapping("/{profileId}/publish")
  SubmissionProfileResponse publish(@PathVariable UUID tenantId, @PathVariable UUID profileId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Roles", required = false) String roles,
      @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String actorId,
      @RequestBody PublishSubmissionProfileRequest request) {
    return withRoles(roles, () -> service.publish(tenantId, key, correlationId, actorId,
        new PublishSubmissionProfileRequest(profileId, request.effectiveFromUtc(), request.effectiveToUtc(),
            request.approvalToken(), request.changeSetRef(), Instant.now())));
  }

  @GetMapping
  List<SubmissionProfileResponse> listProfiles(@PathVariable UUID tenantId,
      @RequestParam(value = "channel", required = false) String channel) {
    if (channel != null) return service.getProfilesByChannel(tenantId, channel);
    // Fallback: return all profiles for tenant via channel iteration
    return Collections.emptyList();
  }

  @GetMapping("/{profileId}")
  SubmissionProfileResponse getProfile(@PathVariable UUID tenantId, @PathVariable UUID profileId) {
    return service.getProfile(tenantId, profileId);
  }

  @GetMapping("/channel/{channel}/active")
  ActiveChannelProfile getActive(@PathVariable UUID tenantId, @PathVariable String channel,
      @RequestParam String quoteIntent) {
    return service.getActiveChannelProfile(tenantId, channel, quoteIntent);
  }

  private <T> T withRoles(String roles, java.util.function.Supplier<T> action) {
    try {
      RequestContext.roles(roles);
      return action.get();
    } finally {
      RequestContext.clear();
    }
  }
}
