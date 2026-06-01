package com.wcpe.scenario.domain;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
class SubmissionProfileService {
  private static final Set<String> ADMIN_ROLES = Set.of("SCENARIO_ADMIN", "SCENARIO_WRITER");
  private static final Set<String> SUBMISSION_PROFILE_MANAGE = Set.of("SCENARIO_ADMIN");
  private static final Set<String> KNOWN_SECTIONS = Set.of("BORROWER_CREDIT", "LOAN_STRUCTURE", "PROPERTY", "INCOME_ASSETS");

  private final SubmissionProfileRepository repository;
  private final ScenarioRepository scenarioRepository;

  SubmissionProfileService(SubmissionProfileRepository repository, ScenarioRepository scenarioRepository) {
    this.repository = repository;
    this.scenarioRepository = scenarioRepository;
  }

  SubmissionProfileResponse createDraft(UUID tenantId, String idempotencyKey, String correlationId, String actorId,
      CreateSubmissionProfileRequest request) {
    requireRole("SCENARIO_ADMIN", SUBMISSION_PROFILE_MANAGE);
    validateProfileRequest(request);
    validateFieldPaths(request.rules());
    Optional<Object> replay = scenarioRepository.idempotent(tenantId.toString() + ":profile", idempotencyKey, request);
    if (replay.isPresent()) return (SubmissionProfileResponse) replay.get();
    UUID profileId = repository.createProfile(tenantId, request.channel(), request.quoteIntent(), request.profileName(),
        request.effectiveFromUtc(), request.effectiveToUtc(), request.rules(), actorId);
    SubmissionProfileResponse response = repository.getProfile(tenantId, profileId);
    scenarioRepository.remember(tenantId.toString() + ":profile", idempotencyKey, request, response);
    return response;
  }

  SubmissionProfileResponse publish(UUID tenantId, String idempotencyKey, String correlationId, String actorId,
      PublishSubmissionProfileRequest request) {
    requireRole("SCENARIO_ADMIN", SUBMISSION_PROFILE_MANAGE);
    Optional<Object> replay = scenarioRepository.idempotent(tenantId.toString() + ":profile-publish", idempotencyKey, request);
    if (replay.isPresent()) return (SubmissionProfileResponse) replay.get();
    UUID versionId = repository.publishProfile(tenantId, request.profileId(), request.effectiveFromUtc(),
        request.effectiveToUtc(), request.approvalToken(), request.changeSetRef(), actorId);
    SubmissionProfileResponse response = repository.getProfile(tenantId, request.profileId());
    scenarioRepository.remember(tenantId.toString() + ":profile-publish", idempotencyKey, request, response);
    return response;
  }

  SubmissionProfileResponse getProfile(UUID tenantId, UUID profileId) {
    return repository.getProfile(tenantId, profileId);
  }

  ActiveChannelProfile getActiveChannelProfile(UUID tenantId, String channel, String quoteIntent) {
    return repository.getActiveChannelProfile(tenantId, channel, quoteIntent, Instant.now());
  }

  List<SubmissionProfileResponse> getProfilesByChannel(UUID tenantId, String channel) {
    List<SubmissionProfile> profiles = repository.findByChannel(tenantId, channel);
    List<SubmissionProfileResponse> responses = new ArrayList<>();
    for (SubmissionProfile p : profiles) {
      SubmissionProfileVersion latest = p.versions().stream().max(Comparator.comparingInt(SubmissionProfileVersion::versionNumber)).orElse(null);
      if (latest != null) {
        List<SubmissionProfileFieldRule> rules = repositoryRules(tenantId, latest);
        responses.add(new SubmissionProfileResponse(p.profileId(), latest.versionId(), latest.status(),
            p.channel(), p.quoteIntent(), p.profileName(), latest.versionNumber(),
            latest.effectiveFromUtc(), latest.effectiveToUtc(), latest.checksum(), rules,
            Collections.emptyList(), latest.createdAtUtc()));
      }
    }
    return responses;
  }

  List<ValidationIssue> validateScenarioAgainstProfile(UUID tenantId, String channel, String quoteIntent, Map<String, Object> scenarioFacts) {
    ActiveChannelProfile profile = repository.getActiveChannelProfile(tenantId, channel, quoteIntent, Instant.now());
    if (profile == null) {
      return List.of(new ValidationIssue("SUBMISSION_PROFILE_NOT_FOUND", "channel." + channel,
          Severity.BLOCKING, "No active submission profile for channel " + channel + " / intent " + quoteIntent + "."));
    }
    List<ValidationIssue> issues = new ArrayList<>();
    for (SubmissionProfileFieldRule rule : profile.rules()) {
      String fieldPath = rule.fieldPath();
      Object value = resolveField(scenarioFacts, fieldPath);
      boolean required = evaluateRequired(rule.requiredWhenExpression());
      if (required && (value == null || (value instanceof String && ((String) value).isBlank()))) {
        issues.add(new ValidationIssue("FIELD_REQUIRED_BY_PROFILE", fieldPath,
            rule.severity() == FieldSeverity.BLOCKING ? Severity.BLOCKING : Severity.WARNING,
            rule.message()));
      }
    }
    return issues;
  }

  private List<SubmissionProfileFieldRule> repositoryRules(UUID tenantId, SubmissionProfileVersion version) {
    return repository.getProfile(tenantId, version.submissionProfileId()).rules();
  }

  private void validateProfileRequest(CreateSubmissionProfileRequest request) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (request.channel() == null || request.channel().isBlank()) issues.add(new ValidationIssue("INVALID_CHANNEL", "channel", Severity.BLOCKING, "Channel is required."));
    if (request.quoteIntent() == null || request.quoteIntent().isBlank()) issues.add(new ValidationIssue("INVALID_QUOTE_INTENT", "quoteIntent", Severity.BLOCKING, "Quote intent is required."));
    if (request.profileName() == null || request.profileName().isBlank()) issues.add(new ValidationIssue("INVALID_PROFILE_NAME", "profileName", Severity.BLOCKING, "Profile name is required."));
    if (request.effectiveFromUtc() == null) issues.add(new ValidationIssue("INVALID_EFFECTIVE_DATE", "effectiveFromUtc", Severity.BLOCKING, "Effective from date is required."));
    if (request.effectiveToUtc() != null && request.effectiveToUtc().isBefore(request.effectiveFromUtc())) issues.add(new ValidationIssue("INVALID_DATE_RANGE", "effectiveToUtc", Severity.BLOCKING, "Effective to must be after effective from."));
    if (request.rules() == null || request.rules().isEmpty()) issues.add(new ValidationIssue("EMPTY_RULES", "rules", Severity.BLOCKING, "At least one rule is required."));
    if (!issues.isEmpty()) throw new ScenarioException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Submission profile validation failed.", issues);
  }

  private void validateFieldPaths(List<SubmissionProfileFieldRule> rules) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (int i = 0; i < rules.size(); i++) {
      SubmissionProfileFieldRule rule = rules.get(i);
      if (!KNOWN_SECTIONS.contains(rule.section())) issues.add(new ValidationIssue("UNKNOWN_SECTION", "rules[" + i + "].section", Severity.BLOCKING, "Section not registered: " + rule.section()));
      if (rule.fieldPath() == null || rule.fieldPath().isBlank()) issues.add(new ValidationIssue("MISSING_FIELD_PATH", "rules[" + i + "].fieldPath", Severity.BLOCKING, "Field path is required."));
      if (rule.message() == null || rule.message().isBlank()) issues.add(new ValidationIssue("MISSING_RULE_MESSAGE", "rules[" + i + "].message", Severity.BLOCKING, "Rule message is required."));
    }
    if (!issues.isEmpty()) throw new ScenarioException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Field rule validation failed.", issues);
  }

  private static Object resolveField(Map<String, Object> facts, String dottedPath) {
    String[] parts = dottedPath.split("\\.");
    Object current = facts;
    for (String part : parts) {
      if (current instanceof Map) current = ((Map<?, ?>) current).get(part);
      else return null;
    }
    return current;
  }

  private static boolean evaluateRequired(String expression) {
    if (expression == null) return false;
    if ("always()".equals(expression.trim())) return true;
    if (expression.trim().contains("true")) return true;
    return false;
  }

  private static void requireRole(String required, Set<String> allowed) {
    String roles = Optional.ofNullable(RequestContext.roles()).orElse("");
    boolean ok = Arrays.stream(roles.split(",")).map(String::trim).anyMatch(allowed::contains);
    if (!ok) throw new ScenarioException(org.springframework.http.HttpStatus.FORBIDDEN, "ROLE_REQUIRED", required + " role is required.", List.of());
  }
}
