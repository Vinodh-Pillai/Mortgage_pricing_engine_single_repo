package com.wcpe.scenario.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {
  private static final Set<String> WRITER_ROLES = Set.of("SCENARIO_WRITER", "SCENARIO_ADMIN");
  private static final Set<String> REPLAY_ROLES = Set.of("SCENARIO_REPLAY", "SCENARIO_ADMIN");
  private static final Map<String, ChannelSubmissionProfile> PROFILES = Map.of(
      "RETAIL", new ChannelSubmissionProfile("RETAIL", Set.of("BORROWER_CREDIT", "LOAN_STRUCTURE", "PROPERTY", "INCOME_ASSETS"), Set.of("PURCHASE", "RATE_TERM_REFI", "CASH_OUT_REFI"), 500),
      "WHOLESALE", new ChannelSubmissionProfile("WHOLESALE", Set.of("BORROWER_CREDIT", "LOAN_STRUCTURE", "PROPERTY", "INCOME_ASSETS"), Set.of("PURCHASE", "RATE_TERM_REFI", "CASH_OUT_REFI"), 1000),
      "CORRESPONDENT", new ChannelSubmissionProfile("CORRESPONDENT", Set.of("BORROWER_CREDIT", "LOAN_STRUCTURE", "PROPERTY", "INCOME_ASSETS"), Set.of("PURCHASE", "RATE_TERM_REFI"), 1000),
      "CONSUMER_DIRECT", new ChannelSubmissionProfile("CONSUMER_DIRECT", Set.of("BORROWER_CREDIT", "LOAN_STRUCTURE", "PROPERTY", "INCOME_ASSETS"), Set.of("PURCHASE", "RATE_TERM_REFI"), 250),
      "PARTNER_API", new ChannelSubmissionProfile("PARTNER_API", Set.of("BORROWER_CREDIT", "LOAN_STRUCTURE", "PROPERTY", "INCOME_ASSETS"), Set.of("PURCHASE", "RATE_TERM_REFI", "CASH_OUT_REFI", "SCENARIO_ANALYSIS"), 5000));
  private final ScenarioRepository repository;

  public ScenarioService(ScenarioRepository repository) {
    this.repository = repository;
  }

  public ScenarioResponse createDraft(UUID tenantId, String idempotencyKey, String correlationId, CreateScenarioRequest request) {
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    validateProfile(request.channel(), request.quoteIntent());
    Optional<Object> replay = repository.idempotent(tenantId.toString(), idempotencyKey, request);
    if (replay.isPresent()) return (ScenarioResponse) replay.get();
    Scenario scenario = new Scenario(tenantId, request.quoteIntent(), request.channel(), request.scenarioName(), request.externalLoanId(), request.sourceSystem(), request.initialFacts());
    repository.save(scenario);
    emit(tenantId, scenario, "ScenarioDraftCreated.v1", correlationId, Map.of("status", scenario.status().name()));
    ScenarioResponse response = response(scenario);
    repository.remember(tenantId.toString(), idempotencyKey, request, response);
    return response;
  }

  public ScenarioResponse get(UUID tenantId, UUID scenarioId) { return response(repository.get(tenantId, scenarioId)); }
  public BorrowerCreditResponse updateBorrowers(UUID tenantId, UUID scenarioId, String key, String correlationId, BorrowerCreditRequest request) {
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
  public ScenarioResponse updateLoan(UUID tenantId, UUID scenarioId, String key, String correlationId, LoanStructureRequest request) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioLoanStructureUpdated.v1", s -> s.updateLoan(request)); }
  public ScenarioResponse updateProperty(UUID tenantId, UUID scenarioId, String key, String correlationId, PropertyRequest request) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioPropertyUpdated.v1", s -> s.updateProperty(request)); }
  public ScenarioResponse updateIncomeAssets(UUID tenantId, UUID scenarioId, String key, String correlationId, IncomeAssetRequest request) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioIncomeAssetsUpdated.v1", s -> s.updateIncomeAssets(request)); }
  public ScenarioResponse normalize(UUID tenantId, UUID scenarioId, String key, String correlationId) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioNormalized.v1", Scenario::normalize); }
  public ScenarioResponse submit(UUID tenantId, UUID scenarioId, String key, String correlationId) { return mutate(tenantId, scenarioId, key, correlationId, "ScenarioSubmitted.v1", Scenario::submit); }

  public ScenarioResponse cloneScenario(UUID tenantId, UUID scenarioId, String key, String correlationId, CloneScenarioRequest request) {
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

  public BatchImportResponse importBatch(UUID tenantId, String key, String correlationId, BatchImportRequest request) {
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId + ":batch", key, request);
    if (replay.isPresent()) return (BatchImportResponse) replay.get();
    int maxBatch = Optional.ofNullable(request.scenarios()).orElse(List.of()).stream().map(CreateScenarioRequest::channel).filter(Objects::nonNull).map(PROFILES::get).filter(Objects::nonNull).mapToInt(ChannelSubmissionProfile::maxBatchSize).min().orElse(250);
    if (Optional.ofNullable(request.scenarios()).orElse(List.of()).size() > maxBatch) throw new ScenarioException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "BATCH_TOO_LARGE", "Batch exceeds channel submission profile limit.", List.of());
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
    requireRole("SCENARIO_REPLAY", REPLAY_ROLES);
    Scenario scenario = repository.get(tenantId, scenarioId);
    int replayVersion = "latest".equals(version) ? scenario.version() : Integer.parseInt(version);
    Map<String, Object> exactSnapshot = repository.versionSnapshot(tenantId, scenarioId, replayVersion).orElseThrow(() -> new ScenarioException(org.springframework.http.HttpStatus.NOT_FOUND, "SCENARIO_VERSION_NOT_FOUND", "Requested scenario version was not found.", List.of()));
    boolean redacted = !"full".equals(redaction);
    Map<String, Object> raw = new LinkedHashMap<>((Map<String, Object>) exactSnapshot.getOrDefault("rawFacts", scenario.rawFacts()));
    if (redacted) raw.replaceAll((k, v) -> k.toLowerCase().contains("income") || k.toLowerCase().contains("asset") ? "REDACTED" : v);
    emit(tenantId, scenario, "ScenarioReplayPackageViewed.v1", null, Map.of("redaction", redaction, "version", version));
    return new ReplayPackage(scenario.scenarioId(), replayVersion, "scenario-v1", redacted, scenario.status(), scenario.versions(), raw,
        (Map<String, Object>) exactSnapshot.getOrDefault("normalizedFacts", scenario.normalizedFacts()), scenario.validationIssues(), repository.events(tenantId, scenarioId), UUID.randomUUID());
  }

  ChannelSubmissionProfile profile(String channel) {
    ChannelSubmissionProfile profile = PROFILES.get(channel);
    if (profile == null) throw new ScenarioException(org.springframework.http.HttpStatus.NOT_FOUND, "CHANNEL_PROFILE_NOT_FOUND", "Channel submission profile was not found.", List.of());
    return profile;
  }

  public List<EventRecord> events(UUID tenantId, UUID scenarioId) {
    repository.get(tenantId, scenarioId);
    return repository.events(tenantId, scenarioId);
  }

  private ScenarioResponse mutate(UUID tenantId, UUID scenarioId, String key, String correlationId, String eventType, java.util.function.Consumer<Scenario> mutation) {
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    Optional<Object> replay = repository.idempotent(tenantId + ":" + scenarioId, key, eventType);
    if (replay.isPresent()) return (ScenarioResponse) replay.get();
    Scenario scenario = repository.get(tenantId, scenarioId);
    mutation.accept(scenario);
    repository.save(scenario);
    emit(tenantId, scenario, eventType, correlationId, Map.of("version", scenario.version()));
    ScenarioResponse response = response(scenario);
    repository.remember(tenantId + ":" + scenarioId, key, eventType, response);
    return response;
  }

  private static void validateProfile(String channel, String quoteIntent) {
    ChannelSubmissionProfile profile = PROFILES.get(channel);
    if (profile == null) throw new ScenarioException(org.springframework.http.HttpStatus.FORBIDDEN, "CHANNEL_PROFILE_REQUIRED", "Channel submission profile is required.", List.of());
    if (!profile.allowedQuoteIntents().contains(quoteIntent)) throw new ScenarioException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_INTENT_NOT_ALLOWED_FOR_CHANNEL", "Quote intent is not allowed for channel.", List.of());
  }

  private static void requireRole(String required, Set<String> allowed) {
    String roles = Optional.ofNullable(RequestContext.roles()).orElse("");
    boolean ok = Arrays.stream(roles.split(",")).map(String::trim).anyMatch(allowed::contains);
    if (!ok) throw new ScenarioException(org.springframework.http.HttpStatus.FORBIDDEN, "ROLE_REQUIRED", required + " role is required.", List.of());
  }

  private void emit(UUID tenantId, Scenario scenario, String eventType, String correlationId, Map<String, Object> payload) {
    String corr = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    EventRecord event = new EventRecord(UUID.randomUUID(), tenantId, scenario.scenarioId(), eventType, 1, corr, Instant.now(), payload);
    repository.event(event);
    repository.audit(new AuditRecord(UUID.randomUUID(), tenantId, scenario.scenarioId(), eventType.replace('.', '_').toUpperCase(), corr, Instant.now(), scenario.replayHash()));
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
}
