package com.wcpe.scenario.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {
  private static final Set<String> WRITER_ROLES = Set.of("SCENARIO_WRITER", "SCENARIO_ADMIN");
  private static final Set<String> REPLAY_ROLES = Set.of("SCENARIO_REPLAY", "SCENARIO_ADMIN");
  private final ScenarioRepository repository;
  private final SubmissionProfileService submissionProfiles;
  private final ScenarioReplayPackageQueryService replayPackages;

  public ScenarioService(ScenarioRepository repository, SubmissionProfileService submissionProfiles, ScenarioReplayPackageQueryService replayPackages) {
    this.repository = repository;
    this.submissionProfiles = submissionProfiles;
    this.replayPackages = replayPackages;
  }

  public synchronized ScenarioResponse createDraft(UUID tenantId, String idempotencyKey, String correlationId, CreateScenarioRequest request) {
    requireIdempotencyKey(idempotencyKey);
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId.toString(), idempotencyKey, request);
    if (replay.isPresent()) return (ScenarioResponse) replay.get();
    requireActiveProfile(tenantId, request.channel(), request.quoteIntent());
    Scenario scenario = new Scenario(tenantId, request.quoteIntent(), request.channel(), request.scenarioName(), request.externalLoanId(), request.sourceSystem(), request.initialFacts());
    applyProfileValidation(scenario);
    repository.save(scenario);
    emit(tenantId, scenario, "ScenarioDraftCreated.v1", correlationId, Map.of("status", scenario.status().name()));
    ScenarioResponse response = response(scenario);
    repository.remember(tenantId.toString(), idempotencyKey, request, response);
    return response;
  }

  synchronized List<ValidationIssue> validateCreateDraft(UUID tenantId, String idempotencyKey, CreateScenarioRequest request) {
    requireIdempotencyKey(idempotencyKey);
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId.toString(), idempotencyKey, request);
    if (replay.isPresent()) return ((ScenarioResponse) replay.get()).validationIssues();
    requireActiveProfile(tenantId, request.channel(), request.quoteIntent());
    Scenario scenario = new Scenario(tenantId, request.quoteIntent(), request.channel(), request.scenarioName(), request.externalLoanId(), request.sourceSystem(), request.initialFacts());
    applyProfileValidation(scenario);
    return scenario.validationIssues();
  }

  public ScenarioResponse get(UUID tenantId, UUID scenarioId) { return response(repository.get(tenantId, scenarioId)); }
  public synchronized BorrowerCreditResponse updateBorrowers(UUID tenantId, UUID scenarioId, String key, String correlationId, BorrowerCreditRequest request) {
    requireIdempotencyKey(key);
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId + ":" + scenarioId + ":borrowers", key, request);
    if (replay.isPresent()) return (BorrowerCreditResponse) replay.get();
    Scenario scenario = repository.get(tenantId, scenarioId);
    scenario.updateBorrowers(request);
    List<BorrowerCredit> borrowers = Optional.ofNullable(request.borrowers()).orElse(List.of());
    RepresentativeCreditScorePolicy.RepresentativeCreditResult repResult = RepresentativeCreditScorePolicy.derive(borrowers);
    repository.save(scenario);
    repository.persistBorrowers(tenantId, scenarioId, scenario.version(), borrowers);
    repository.persistRepresentativeCredit(tenantId, scenarioId, scenario.version(), repResult);
    emit(tenantId, scenario, "ScenarioBorrowerCreditUpdated.v1", correlationId, Map.of("version", scenario.version(), "borrowerCount", borrowers.size()));
    BorrowerCreditResponse creditResponse = borrowerCreditResponse(scenario, borrowers, repResult);
    repository.remember(tenantId + ":" + scenarioId + ":borrowers", key, request, creditResponse);
    return creditResponse;
  }
  public synchronized LoanStructureResponse updateLoan(UUID tenantId, UUID scenarioId, String key, String correlationId, LoanStructureRequest request) {
    requireIdempotencyKey(key);
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId + ":" + scenarioId + ":loan-structure", key, request);
    if (replay.isPresent()) return (LoanStructureResponse) replay.get();
    Scenario scenario = repository.get(tenantId, scenarioId);
    LoanMetricResult result = scenario.updateLoan(request);
    repository.save(scenario);
    repository.persistLoanStructure(tenantId, scenarioId, scenario.version(), request, result);
    UUID auditPackageId = emit(tenantId, scenario, "ScenarioLoanStructureUpdated.v1", correlationId, loanStructurePayload(scenario, request, result));
    LoanStructureResponse response = loanStructureResponse(scenario, result, auditPackageId);
    repository.remember(tenantId + ":" + scenarioId + ":loan-structure", key, request, response);
    return response;
  }
  public synchronized ScenarioResponse updateProperty(UUID tenantId, UUID scenarioId, String key, String correlationId, PropertyRequest request) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioPropertyUpdated.v1", request, s -> s.updateProperty(request)); }
  public synchronized ScenarioResponse updateIncomeAssets(UUID tenantId, UUID scenarioId, String key, String correlationId, IncomeAssetRequest request) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioIncomeAssetsUpdated.v1", request, s -> s.updateIncomeAssets(request)); }
  public synchronized ScenarioResponse normalize(UUID tenantId, UUID scenarioId, String key, String correlationId) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioNormalized.v1", "ScenarioNormalized.v1", Scenario::normalize); }
  public synchronized ScenarioResponse submit(UUID tenantId, UUID scenarioId, String key, String correlationId) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioSubmitted.v1", "ScenarioSubmitted.v1", Scenario::submit); }

  public synchronized ScenarioResponse cloneScenario(UUID tenantId, UUID scenarioId, String key, String correlationId, CloneScenarioRequest request) {
    requireIdempotencyKey(key);
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId + ":clone:" + scenarioId, key, request);
    if (replay.isPresent()) return (ScenarioResponse) replay.get();
    Scenario source = repository.get(tenantId, scenarioId);
    Scenario clone = Scenario.cloneOf(source, request.scenarioName(), request.overrides());
    repository.save(clone);
    emit(tenantId, clone, "ScenarioCloned.v1", correlationId, Map.of("sourceScenarioId", scenarioId.toString()));
    ScenarioResponse response = response(clone);
    repository.remember(tenantId + ":clone:" + scenarioId, key, request, response);
    return response;
  }

  public synchronized BatchImportResponse importBatch(UUID tenantId, String key, String correlationId, BatchImportRequest request) {
    requireIdempotencyKey(key);
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId + ":batch", key, request);
    if (replay.isPresent()) return (BatchImportResponse) replay.get();
    List<ScenarioResponse> accepted = new ArrayList<>();
    List<ValidationIssue> rejected = new ArrayList<>();
    for (CreateScenarioRequest item : Optional.ofNullable(request.scenarios()).orElse(List.of())) {
      try { accepted.add(createDraft(tenantId, UUID.randomUUID().toString(), correlationId, item)); }
      catch (ScenarioException ex) { rejected.addAll(ex.fieldErrors()); }
    }
    BatchImportResponse response = new BatchImportResponse(UUID.randomUUID(), accepted.size(), rejected.size(), accepted, rejected);
    repository.remember(tenantId + ":batch", key, request, response);
    return response;
  }

  public ReplayPackage replay(UUID tenantId, UUID scenarioId, String version, String redaction) {
    return replay(tenantId, scenarioId, new ScenarioReplayAccessRequest(version, redaction, false, null, null));
  }

  public ReplayPackage replay(UUID tenantId, UUID scenarioId, ScenarioReplayAccessRequest request) {
    return replayPackages.replay(tenantId, scenarioId, request);
  }

  ChannelSubmissionProfile profile(String channel) {
    throw new ScenarioException(org.springframework.http.HttpStatus.GONE, "STATIC_CHANNEL_PROFILE_REMOVED", "Channel submission profiles are tenant-governed and must be read through published submission profiles.", List.of());
  }

  public List<EventRecord> events(UUID tenantId, UUID scenarioId) {
    repository.get(tenantId, scenarioId);
    return repository.events(tenantId, scenarioId);
  }

  public boolean wasIdempotencyReplayed() { return repository.consumeReplayFlag(); }

  private ScenarioResponse mutate(UUID tenantId, UUID scenarioId, String key, String correlationId, String eventType, Object requestForHash, java.util.function.Consumer<Scenario> mutation) {
    requireIdempotencyKey(key);
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId + ":" + scenarioId + ":" + eventType, key, requestForHash);
    if (replay.isPresent()) return (ScenarioResponse) replay.get();
    Scenario scenario = repository.get(tenantId, scenarioId);
    mutation.accept(scenario);
    applyProfileValidation(scenario);
    repository.save(scenario);
    emit(tenantId, scenario, eventType, correlationId, Map.of("version", scenario.version()));
    ScenarioResponse response = response(scenario);
    repository.remember(tenantId + ":" + scenarioId + ":" + eventType, key, requestForHash, response);
    return response;
  }

  private static void requireIdempotencyKey(String key) {
    if (key == null || key.isBlank()) {
      throw new ScenarioException(org.springframework.http.HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required for scenario mutations.", List.of());
    }
  }

  private void requireActiveProfile(UUID tenantId, String channel, String quoteIntent) {
    ActiveChannelProfile profile = submissionProfiles.getActiveChannelProfile(tenantId, channel, quoteIntent);
    if (profile == null) throw new ScenarioException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "SUBMISSION_PROFILE_NOT_FOUND", "No active submission profile exists for this channel and quote intent.", List.of(new ValidationIssue("SUBMISSION_PROFILE_NOT_FOUND", "channel", Severity.BLOCKING, "No active submission profile exists for this channel and quote intent.")));
  }

  private void applyProfileValidation(Scenario scenario) {
    scenario.applySubmissionProfileIssues(submissionProfiles.validateScenarioAgainstProfile(scenario.tenantId(), scenario.channel(), scenario.quoteIntent(), scenario.scenarioFacts()));
  }

  private static void requireRole(String required, Set<String> allowed) {
    String roles = Optional.ofNullable(RequestContext.roles()).orElse("");
    boolean ok = Arrays.stream(roles.split(",")).map(String::trim).anyMatch(allowed::contains);
    if (!ok) throw new ScenarioException(org.springframework.http.HttpStatus.FORBIDDEN, "ROLE_REQUIRED", required + " role is required.", List.of());
  }

  private UUID emit(UUID tenantId, Scenario scenario, String eventType, String correlationId, Map<String, Object> payload) {
    String corr = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    EventRecord event = new EventRecord(UUID.randomUUID(), tenantId, scenario.scenarioId(), eventType, 1, corr, Instant.now(), payload);
    repository.event(event);
    UUID auditPackageId = UUID.randomUUID();
    repository.audit(new AuditRecord(auditPackageId, tenantId, scenario.scenarioId(), eventType.replace('.', '_').toUpperCase(), corr, Instant.now(), scenario.replayHash()));
    return auditPackageId;
  }

  private static ScenarioResponse response(Scenario scenario) {
    long blocking = scenario.validationIssues().stream().filter(i -> i.severity() == Severity.BLOCKING).count();
    long warning = scenario.validationIssues().stream().filter(i -> i.severity() == Severity.WARNING).count();
    return new ScenarioResponse(scenario.scenarioId(), scenario.version(), scenario.status(), scenario.quoteIntent(), scenario.channel(), scenario.completedSections(),
        (int) blocking, (int) warning, scenario.derivedFields(), UUID.randomUUID(), scenario.replayHash(), scenario.validationIssues());
  }

  private static BorrowerCreditResponse borrowerCreditResponse(Scenario scenario, List<BorrowerCredit> borrowers, RepresentativeCreditScorePolicy.RepresentativeCreditResult result) {
    long blocking = scenario.validationIssues().stream().filter(i -> i.severity() == Severity.BLOCKING).count();
    long warning = scenario.validationIssues().stream().filter(i -> i.severity() == Severity.WARNING).count();
    List<String> sections = scenario.completedSections().contains("BORROWER_CREDIT") ? List.of("BORROWER_CREDIT") : List.of();
    return new BorrowerCreditResponse(scenario.scenarioId(), scenario.version(),
        result.qualityStatus(), result.score(), result.rule(),
        borrowers.size(), (int) blocking, (int) warning,
        sections, UUID.randomUUID());
  }

  private static LoanStructureResponse loanStructureResponse(Scenario scenario, LoanMetricResult result, UUID auditPackageId) {
    long blocking = scenario.validationIssues().stream().filter(i -> i.severity() == Severity.BLOCKING).count();
    long warning = scenario.validationIssues().stream().filter(i -> i.severity() == Severity.WARNING).count();
    Map<String, BigDecimal> metrics = new LinkedHashMap<>();
    for (LoanMetric metric : result.metrics()) {
      if (metric.ratioValue() != null) metrics.put(metric.metricCode().toLowerCase(Locale.ROOT), metric.ratioValue());
      if (metric.bpsValue() != null) metrics.put(metric.metricCode().toLowerCase(Locale.ROOT) + "Bps", metric.bpsValue());
    }
    return new LoanStructureResponse(scenario.scenarioId(), scenario.version(), result.qualityStatus(), metrics,
        result.calculationTraceId(), (int) blocking, (int) warning, auditPackageId, scenario.validationIssues());
  }

  private static Map<String, Object> loanStructurePayload(Scenario scenario, LoanStructureRequest request, LoanMetricResult result) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("scenarioId", scenario.scenarioId().toString());
    payload.put("scenarioVersion", scenario.version());
    payload.put("loanPurpose", request.loanPurpose());
    payload.put("loanAmountHash", Hashing.sha256(String.valueOf(request.loanAmount())));
    payload.put("termMonths", request.termMonths());
    payload.put("requestedLockPeriodDays", request.requestedLockPeriodDays());
    payload.put("calculationTraceId", result.calculationTraceId().toString());
    payload.put("metrics", result.metrics());
    payload.put("blockingIssueCount", result.issues().stream().filter(i -> i.severity() == Severity.BLOCKING).count());
    payload.put("warningIssueCount", result.issues().stream().filter(i -> i.severity() == Severity.WARNING).count());
    return payload;
  }
}
