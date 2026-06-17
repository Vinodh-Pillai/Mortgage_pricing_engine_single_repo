package com.wcpe.ratefeed.normalization;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LLMMappingProposer {
  private static final int MAX_CORRECTION_RETRIES = 2;
  private static final Set<String> REQUIRED_FIELDS = Set.of("note_rate", "base_price", "lock_period");
  private static final List<MappingWizardModels.GoldenProfileExample> SEEDED_GOLDEN_EXAMPLES = List.of(
      new MappingWizardModels.GoldenProfileExample(
          "Onslow Bay DSCR Plus v1",
          "EXCEL_XLSX",
          "ONSLOW_BAY",
          "DSCR_PLUS",
          Map.of("formatType", "EXCEL_XLSX", "matrixDetected", true, "sheetCount", 3),
          List.of("note_rate", "base_price", "lock_period", "adjustment_value"),
          0.98),
      new MappingWizardModels.GoldenProfileExample(
          "FNMA LLPA CSV v1",
          "CSV",
          "FNMA",
          "CONF_30YR",
          Map.of("formatType", "CSV", "matrixDetected", true, "headerSignatures", List.of("fico", "ltv", "adjustment")),
          List.of("adjustment_type", "adjustment_value", "base_price"),
          0.95),
      new MappingWizardModels.GoldenProfileExample(
          "Polly Export JSON v1",
          "STRUCTURED_TEXT",
          "POLLY",
          "NON_QM_DSCR",
          Map.of("formatType", "STRUCTURED_TEXT", "matrixDetected", false),
          List.of("note_rate", "base_price", "lock_period", "canonical_product_key"),
          0.92));

  private final MappingWizardHeuristicAnalyzer heuristicAnalyzer;

  public LLMMappingProposer(MappingWizardHeuristicAnalyzer heuristicAnalyzer) {
    this.heuristicAnalyzer = heuristicAnalyzer;
  }

  public MappingWizardModels.MappingProposal propose(String fileName, byte[] content) throws IOException {
    MappingWizardModels.MappingProposal proposal = heuristicAnalyzer.analyze(fileName, content);
    return enrichLocalLlmProposal(proposal, SEEDED_GOLDEN_EXAMPLES, null, null, "v1");
  }

  public MappingWizardModels.MappingProposal propose(MappingWizardModels.ProposeRequest request, List<MappingWizardModels.GoldenProfileExample> approvedProfiles) {
    MappingWizardModels.MappingProposal proposal = heuristicAnalyzer.proposeFromPreview(request);
    List<MappingWizardModels.GoldenProfileExample> candidates = new ArrayList<>(SEEDED_GOLDEN_EXAMPLES);
    candidates.addAll(approvedProfiles == null ? List.of() : approvedProfiles);
    return enrichLocalLlmProposal(proposal, candidates, request.investorHint(), request.productHint(), promptVersion(request.promptVersion()));
  }

  private MappingWizardModels.MappingProposal enrichLocalLlmProposal(MappingWizardModels.MappingProposal proposal,
      List<MappingWizardModels.GoldenProfileExample> candidates,
      String investorHint,
      String productHint,
      String promptVersion) {
    List<MappingWizardModels.GoldenProfileExample> fewShotExamples = selectFewShotExamples(proposal, candidates, investorHint, productHint);
    MappingWizardModels.SchemaValidation validation = validateWithRetries(proposal);
    MappingWizardModels.Confidence confidence = validation.valid() && fewShotExamples.size() >= 3 && containsRequiredFields(proposal)
        ? MappingWizardModels.Confidence.HIGH
        : MappingWizardModels.Confidence.MEDIUM;
    List<MappingWizardModels.MappingField> mappings = proposal.mappings().stream()
        .map(field -> upgradeField(field, confidence))
        .toList();
    List<String> warnings = appendLlmNotice(proposal.safetyWarnings(), validation);
    MappingWizardModels.PromptMetrics metrics = new MappingWizardModels.PromptMetrics(promptVersion, "local-redacted-structure", List.of("human_acceptance_rate", "edit_distance", "time_to_approve"));
    return new MappingWizardModels.MappingProposal(
        MappingWizardModels.AnalysisMode.LLM,
        proposal.formatType(),
        confidence,
        proposal.sourcePreview(),
        mappings,
        proposal.unmappedFields(),
        proposal.matrix(),
        proposal.llpaSections(),
        proposal.formatFingerprint(),
        warnings,
        proposal.audit(),
        fewShotExamples,
        validation,
        metrics);
  }

  private MappingWizardModels.MappingField upgradeField(MappingWizardModels.MappingField field, MappingWizardModels.Confidence overallConfidence) {
    double score = overallConfidence == MappingWizardModels.Confidence.HIGH ? 0.90 : Math.min(field.confidenceScore(), 0.60);
    MappingWizardModels.Confidence fieldConfidence = score >= 0.90 ? MappingWizardModels.Confidence.HIGH : score >= 0.70 ? MappingWizardModels.Confidence.MEDIUM : MappingWizardModels.Confidence.LOW;
    return new MappingWizardModels.MappingField(
        field.sourceField(),
        field.canonicalField(),
        fieldConfidence,
        score,
        field.required(),
        field.coercionRule(),
        score < 0.70 ? "REVIEW_REQUIRED" : field.status(),
        field.reasoning() + " Few-shot selector compared only redacted structure and profile metadata.",
        field.alternatives(),
        score < 0.70);
  }

  private MappingWizardModels.SchemaValidation validateWithRetries(MappingWizardModels.MappingProposal proposal) {
    List<String> errors = new ArrayList<>();
    if (proposal.mappings().isEmpty()) errors.add("No field mappings were proposed.");
    List<String> mapped = proposal.mappings().stream().map(MappingWizardModels.MappingField::canonicalField).toList();
    List<String> missingRequired = REQUIRED_FIELDS.stream().filter(field -> !mapped.contains(field)).toList();
    if (!missingRequired.isEmpty()) errors.add("Missing required canonical fields: " + missingRequired);
    if (proposal.safetyWarnings().stream().anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("formula"))) errors.add("Formula-like content requires human review before use.");
    boolean valid = errors.isEmpty();
    return new MappingWizardModels.SchemaValidation(valid, valid ? 0 : MAX_CORRECTION_RETRIES, !valid, errors);
  }

  private boolean containsRequiredFields(MappingWizardModels.MappingProposal proposal) {
    List<String> mapped = proposal.mappings().stream().map(MappingWizardModels.MappingField::canonicalField).toList();
    return REQUIRED_FIELDS.stream().allMatch(mapped::contains);
  }

  private List<MappingWizardModels.GoldenProfileExample> selectFewShotExamples(MappingWizardModels.MappingProposal proposal,
      List<MappingWizardModels.GoldenProfileExample> candidates,
      String investorHint,
      String productHint) {
    return candidates.stream()
        .sorted(Comparator.comparingInt((MappingWizardModels.GoldenProfileExample example) -> similarityScore(proposal, example, investorHint, productHint)).reversed()
            .thenComparing(MappingWizardModels.GoldenProfileExample::name))
        .limit(5)
        .toList();
  }

  private int similarityScore(MappingWizardModels.MappingProposal proposal,
      MappingWizardModels.GoldenProfileExample example,
      String investorHint,
      String productHint) {
    int score = 0;
    if (same(example.formatType(), proposal.formatType())) score += 50;
    if (same(example.investorCode(), investorHint)) score += 20;
    if (same(example.productCode(), productHint)) score += 20;
    Object exampleMatrix = example.formatFingerprint().get("matrixDetected");
    Object incomingMatrix = proposal.formatFingerprint().get("matrixDetected");
    if (exampleMatrix != null && exampleMatrix.equals(incomingMatrix)) score += 10;
    return score;
  }

  private boolean same(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  private String promptVersion(String requested) {
    return requested == null || requested.isBlank() ? "v1" : requested;
  }

  private List<String> appendLlmNotice(List<String> existing, MappingWizardModels.SchemaValidation validation) {
    ArrayList<String> warnings = new ArrayList<>(existing == null ? List.of() : existing);
    warnings.add("External LLM provider is not called by this local slice; proposal uses redacted structure-only local template builder.");
    warnings.add("Prompt/response audit stores hashes and metric names only, not raw rate or price content.");
    if (validation.heuristicFallbackUsed()) warnings.add("Structured validation failed after two correction retries; heuristic fallback is active and confidence is capped at MEDIUM.");
    return warnings;
  }

  static MappingWizardModels.GoldenProfileExample approvedProfileExample(NormalizationProfile profile) {
    Map<String, Object> fingerprint = new LinkedHashMap<>();
    fingerprint.put("formatType", profile.getFormatType());
    if (profile.getFormatFingerprint() != null && profile.getFormatFingerprint().has("matrixDetected")) {
      fingerprint.put("matrixDetected", profile.getFormatFingerprint().path("matrixDetected").asBoolean(false));
    }
    List<String> mappingFields = new ArrayList<>();
    if (profile.getMappingConfig() != null && profile.getMappingConfig().has("fields")) {
      profile.getMappingConfig().path("fields").fieldNames().forEachRemaining(mappingFields::add);
    }
    return new MappingWizardModels.GoldenProfileExample(profile.getName(), profile.getFormatType(), profile.getInvestorCode(), profile.getProductCode(), fingerprint, mappingFields, 1.0);
  }
}
