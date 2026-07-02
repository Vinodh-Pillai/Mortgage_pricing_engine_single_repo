package com.wcpe.ratefeed.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MappingWizardService {
  private static final int FULL_AUTO_THRESHOLD = 95;
  private static final int AUTO_MATCH_THRESHOLD = 80;
  private static final int REVIEW_THRESHOLD = 60;

  private final MappingWizardHeuristicAnalyzer heuristicAnalyzer;
  private final LLMMappingProposer llmMappingProposer;
  private final ObjectMapper mapper;
  private final NormalizationProfileRepository profileRepository;

  public MappingWizardService(MappingWizardHeuristicAnalyzer heuristicAnalyzer, LLMMappingProposer llmMappingProposer, ObjectMapper mapper) {
    this(heuristicAnalyzer, llmMappingProposer, mapper, null);
  }

  @Autowired
  public MappingWizardService(MappingWizardHeuristicAnalyzer heuristicAnalyzer, LLMMappingProposer llmMappingProposer, ObjectMapper mapper,
                               NormalizationProfileRepository profileRepository) {
    this.heuristicAnalyzer = heuristicAnalyzer;
    this.llmMappingProposer = llmMappingProposer;
    this.mapper = mapper;
    this.profileRepository = profileRepository;
  }

  public MappingWizardModels.MappingProposal analyze(String fileName, byte[] content, MappingWizardModels.AnalysisMode mode) throws IOException {
    return mode == MappingWizardModels.AnalysisMode.LLM ? llmMappingProposer.propose(fileName, content) : heuristicAnalyzer.analyze(fileName, content);
  }

  public MappingWizardModels.MappingProposal propose(MappingWizardModels.ProposeRequest request) {
    return request.mode() == MappingWizardModels.AnalysisMode.LLM
        ? llmMappingProposer.propose(request, List.of())
        : heuristicAnalyzer.proposeFromPreview(request);
  }

  public MappingWizardModels.MappingProposal propose(UUID tenantId, MappingWizardModels.ProposeRequest request) {
    return request.mode() == MappingWizardModels.AnalysisMode.LLM
        ? llmMappingProposer.propose(request, approvedGoldenProfiles(tenantId))
        : heuristicAnalyzer.proposeFromPreview(request);
  }

  public MappingWizardModels.PreviewResponse preview(MappingWizardModels.PreviewRequest request) {
    List<Map<String, Object>> output = new ArrayList<>();
    for (Map<String, Object> row : request.sampleRows() == null ? List.<Map<String, Object>>of() : request.sampleRows()) {
      Map<String, Object> normalized = new LinkedHashMap<>();
      request.mappingConfig().path("fields").fields().forEachRemaining(entry -> normalized.put(entry.getKey(), row.get(entry.getValue().asText())));
      output.add(normalized);
    }
    ObjectNode sample = mapper.createObjectNode();
    sample.put("rowCount", output.size());
    sample.put("mappingFields", request.mappingConfig().path("fields").size());
    return new MappingWizardModels.PreviewResponse(output, sample, List.of());
  }

  @Transactional
  public MappingWizardModels.ProfileResponse createProfile(UUID tenantId, MappingWizardModels.CreateProfileRequest request, String actor) {
    requireText(request.name(), "Profile name is required.");
    requireText(request.investorCode(), "Investor code is required.");
    requireText(request.productCode(), "Product code is required.");
    rejectUnsafeJson(request.mappingConfig());
    String formatType = request.formatFingerprint() == null ? "UNKNOWN" : request.formatFingerprint().path("formatType").asText("UNKNOWN");
    NormalizationProfile profile = new NormalizationProfile(tenantId, request.name(), formatType, request.investorCode(), request.productCode(), request.mappingConfig(), request.formatFingerprint(), actor);
    profile.setSampleOutput(request.sampleOutput());
    return toResponse(requireRepository().save(profile));
  }

  @Transactional(readOnly = true)
  public MappingWizardModels.ProfileListResponse listProfiles(UUID tenantId) {
    List<MappingWizardModels.ProfileResponse> list = requireRepository().findByTenantId(tenantId).stream()
        .sorted(Comparator.comparing(NormalizationProfile::getName).thenComparing(NormalizationProfile::getVersion))
        .map(this::toResponse)
        .toList();
    return new MappingWizardModels.ProfileListResponse(list, list.size());
  }

  @Transactional(readOnly = true)
  public MappingWizardModels.ProfileResponse getProfile(UUID tenantId, UUID profileId) { return toResponse(findProfile(tenantId, profileId)); }

  @Transactional
  public MappingWizardModels.ProfileResponse updateDraft(UUID tenantId, UUID profileId, MappingWizardModels.UpdateProfileRequest request, String actor) {
    NormalizationProfile profile = findProfile(tenantId, profileId);
    requireStatus(profile, "DRAFT");
    rejectUnsafeJson(request.mappingConfig());
    profile.newVersion(request.mappingConfig(), actor);
    profile.setSampleOutput(request.sampleOutput());
    return toResponse(requireRepository().save(profile));
  }

  @Transactional
  public MappingWizardModels.GovernanceResponse simulate(UUID tenantId, UUID profileId) {
    NormalizationProfile profile = findProfile(tenantId, profileId);
    requireStatus(profile, "DRAFT");
    profile.simulate();
    requireRepository().save(profile);
    return new MappingWizardModels.GovernanceResponse(profile.getProfileId(), profile.getStatus(), profile.getVersion(), List.of("Review normalized preview output", "Approve profile when simulation evidence is acceptable"));
  }

  @Transactional
  public MappingWizardModels.GovernanceResponse approve(UUID tenantId, UUID profileId, String actor) {
    NormalizationProfile profile = findProfile(tenantId, profileId);
    requireStatus(profile, "SIMULATE");
    profile.approve(actor);
    requireRepository().save(profile);
    return new MappingWizardModels.GovernanceResponse(profile.getProfileId(), profile.getStatus(), profile.getVersion(), List.of("Publish approved profile"));
  }

  @Transactional
  public MappingWizardModels.GovernanceResponse publish(UUID tenantId, UUID profileId) {
    NormalizationProfile profile = findProfile(tenantId, profileId);
    requireStatus(profile, "APPROVED");
    profile.publish();
    requireRepository().save(profile);
    return new MappingWizardModels.GovernanceResponse(profile.getProfileId(), profile.getStatus(), profile.getVersion(), List.of("Auto-match future imports at score >= " + AUTO_MATCH_THRESHOLD));
  }

  @Transactional
  public MappingWizardModels.ProfileResponse newVersion(UUID tenantId, UUID profileId, MappingWizardModels.UpdateProfileRequest request, String actor) {
    NormalizationProfile source = findProfile(tenantId, profileId);
    rejectUnsafeJson(request.mappingConfig());
    NormalizationProfile next = new NormalizationProfile(tenantId, source.getName(), source.getFormatType(), source.getInvestorCode(), source.getProductCode(), request.mappingConfig(), source.getFormatFingerprint(), actor);
    while (next.getVersion() < source.getVersion() + 1) next.newVersion(request.mappingConfig(), actor);
    next.setSampleOutput(request.sampleOutput());
    return toResponse(requireRepository().save(next));
  }

  @Transactional(readOnly = true)
  public MappingWizardModels.AutoMatchResponse autoMatch(UUID tenantId, JsonNode incomingFingerprint) {
    List<ScoredProfile> scored = requireRepository().findAllPublishedByTenant(tenantId).stream()
        .map(profile -> scoreProfile(profile, incomingFingerprint))
        .sorted(Comparator.comparing(ScoredProfile::score).reversed()
            .thenComparing(candidate -> candidate.profile().getName())
            .thenComparing(candidate -> candidate.profile().getVersion()))
        .toList();
    ScoredProfile best = scored.isEmpty() ? null : scored.get(0);
    int bestScore = best == null ? 0 : best.score();
    boolean autoApply = best != null && bestScore >= AUTO_MATCH_THRESHOLD;
    String routingAction = routingAction(bestScore);
    MappingWizardModels.ProfileResponse selected = autoApply ? toResponse(best.profile()) : null;
    List<MappingWizardModels.AutoMatchCandidate> reviewMatches = scored.stream()
        .filter(candidate -> candidate.score() >= REVIEW_THRESHOLD)
        .limit(3)
        .map(candidate -> new MappingWizardModels.AutoMatchCandidate(candidate.profile().getProfileId(), candidate.profile().getName(), candidate.profile().getVersion(), candidate.score(), candidate.matchedFields(), candidate.missingFields()))
        .toList();
    MappingWizardModels.DriftEvidence drift = best == null
        ? new MappingWizardModels.DriftEvidence(List.of(), incomingSignatures(incomingFingerprint).stream().toList(), false)
        : driftEvidence(best.profile(), incomingFingerprint, best.missingFields());
    String fallbackReason = autoApply ? "" : fallbackReason(bestScore);
    return new MappingWizardModels.AutoMatchResponse(selected, bestScore, autoApply, fallbackReason, routingAction, best == null ? List.of() : best.matchedFields(), best == null ? List.of() : best.missingFields(), reviewMatches, drift);
  }

  private int fingerprintScore(NormalizationProfile profile, JsonNode incoming) {
    return scoreProfile(profile, incoming).score();
  }

  private ScoredProfile scoreProfile(NormalizationProfile profile, JsonNode incoming) {
    if (profile == null || incoming == null) return new ScoredProfile(profile, 0, List.of(), List.of());
    JsonNode existing = profile.getFormatFingerprint();
    int score = 0;
    List<String> matched = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    if (nonBlankEquals(profile.getFormatType(), incoming.path("formatType").asText(null))) { score += 40; matched.add("formatType"); }
    else missing.add("formatType");
    if (nonBlankEquals(profile.getInvestorCode(), incoming.path("investorCode").asText(null))) { score += 20; matched.add("investorCode"); }
    else missing.add("investorCode");
    if (nonBlankEquals(profile.getProductCode(), incoming.path("productCode").asText(null))) { score += 20; matched.add("productCode"); }
    else missing.add("productCode");
    int headerScore = headerSignatureScore(existing, incoming);
    if (headerScore > 0) matched.add("headerSignatures");
    else missing.add("headerSignatures");
    score += headerScore;
    return new ScoredProfile(profile, Math.min(score, 100), List.copyOf(matched), List.copyOf(missing));
  }

  private int headerSignatureScore(JsonNode existing, JsonNode incoming) {
    Set<String> expected = signatures(existing);
    Set<String> actual = incomingSignatures(incoming);
    if (expected.isEmpty() || actual.isEmpty()) return 0;
    int matched = 0;
    for (String signature : expected) if (actual.contains(signature)) matched++;
    return Math.min(20, (int) Math.round((matched * 20.0d) / expected.size()));
  }

  private Set<String> signatures(JsonNode fingerprint) {
    Set<String> signatures = new LinkedHashSet<>();
    if (fingerprint == null) return signatures;
    JsonNode headers = fingerprint.path("headerSignatures");
    if (headers.isArray()) headers.forEach(header -> { if (header.isTextual() && !header.asText().isBlank()) signatures.add(header.asText().trim()); });
    JsonNode fields = fingerprint.path("fieldSignatures");
    if (fields.isArray()) fields.forEach(field -> { if (field.isTextual() && !field.asText().isBlank()) signatures.add(field.asText().trim()); });
    return signatures;
  }

  private Set<String> incomingSignatures(JsonNode incoming) { return signatures(incoming); }

  private MappingWizardModels.DriftEvidence driftEvidence(NormalizationProfile profile, JsonNode incoming, List<String> missingFields) {
    Set<String> expected = signatures(profile.getFormatFingerprint());
    Set<String> actual = incomingSignatures(incoming);
    List<String> missing = expected.stream().filter(signature -> !actual.contains(signature)).toList();
    List<String> added = actual.stream().filter(signature -> !expected.contains(signature)).toList();
    return new MappingWizardModels.DriftEvidence(missing, added, !missing.isEmpty() || !added.isEmpty() || missingFields.contains("headerSignatures"));
  }

  private String routingAction(int score) {
    if (score >= FULL_AUTO_THRESHOLD) return "FULL_AUTO";
    if (score >= AUTO_MATCH_THRESHOLD) return "HUMAN_VALIDATE";
    if (score >= REVIEW_THRESHOLD) return "REVIEW_TOP_3";
    return "MAPPING_WIZARD";
  }

  private String fallbackReason(int score) {
    if (score >= REVIEW_THRESHOLD) return "Published profile match scored below " + AUTO_MATCH_THRESHOLD + "; queue top 3 matches for human review.";
    return "No published profile matched at score >= " + REVIEW_THRESHOLD + "; launch mapping wizard.";
  }

  private boolean nonBlankEquals(String expected, String actual) {
    return expected != null && !expected.isBlank() && actual != null && !actual.isBlank() && expected.equals(actual);
  }

  private List<MappingWizardModels.GoldenProfileExample> approvedGoldenProfiles(UUID tenantId) {
    return requireRepository().findByTenantId(tenantId).stream()
        .filter(profile -> "APPROVED".equals(profile.getStatus()) || "PUBLISHED".equals(profile.getStatus()))
        .sorted(Comparator.comparing(NormalizationProfile::getName).thenComparing(NormalizationProfile::getVersion))
        .map(LLMMappingProposer::approvedProfileExample)
        .toList();
  }

  private NormalizationProfile findProfile(UUID tenantId, UUID profileId) {
    NormalizationProfile profile = requireRepository().findById(profileId)
        .filter(candidate -> tenantId.equals(candidate.getTenantId()))
        .orElse(null);
    if (profile == null) throw new RateFeedException(HttpStatus.NOT_FOUND, "NORMALIZATION_PROFILE_NOT_FOUND", "Normalization profile was not found.");
    return profile;
  }

  private NormalizationProfileRepository requireRepository() {
    if (profileRepository == null) {
      throw new RateFeedException(HttpStatus.SERVICE_UNAVAILABLE, "NORMALIZATION_PROFILE_STORE_UNAVAILABLE", "Normalization profile persistence is required; in-memory profile storage is disabled.");
    }
    return profileRepository;
  }

  private void requireStatus(NormalizationProfile profile, String status) {
    if (!status.equals(profile.getStatus())) throw new RateFeedException(HttpStatus.CONFLICT, "INVALID_PROFILE_STATUS", "Profile must be " + status + " but was " + profile.getStatus() + ".");
  }

  private void requireText(String value, String message) {
    if (value == null || value.isBlank()) throw new RateFeedException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_REQUEST", message);
  }

  private void rejectUnsafeJson(JsonNode node) {
    if (node == null) throw new RateFeedException(HttpStatus.BAD_REQUEST, "INVALID_MAPPING_CONFIG", "Mapping config is required.");
    if (containsFormulaLikeText(node)) throw new RateFeedException(HttpStatus.BAD_REQUEST, "UNSAFE_MAPPING_CONFIG", "Formula-like mapping values are rejected.");
  }

  private boolean containsFormulaLikeText(JsonNode node) {
    if (node.isTextual()) {
      String text = node.asText().trim();
      return text.startsWith("=") || text.startsWith("+") || text.startsWith("-") || text.startsWith("@");
    }
    if (node.isContainerNode()) for (JsonNode child : node) if (containsFormulaLikeText(child)) return true;
    return false;
  }

  private MappingWizardModels.ProfileResponse toResponse(NormalizationProfile profile) {
    return new MappingWizardModels.ProfileResponse(profile.getProfileId(), profile.getTenantId(), profile.getName(), profile.getFormatType(), profile.getInvestorCode(), profile.getProductCode(), profile.getVersion(), profile.getStatus(), profile.getMappingConfig(), profile.getFormatFingerprint(), profile.getSampleOutput());
  }

  private record ScoredProfile(NormalizationProfile profile, int score, List<String> matchedFields, List<String> missingFields) {}
}
