package com.wcpe.ratefeed.normalization;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MappingWizardModels {
  private MappingWizardModels() {}

  public enum AnalysisMode { LLM, HEURISTIC }

  public enum Confidence { HIGH, MEDIUM, LOW }

  public record Alternative(String canonicalField, double score, String reason) {}

  public record GoldenProfileExample(
      String name,
      String formatType,
      String investorCode,
      String productCode,
      Map<String, Object> formatFingerprint,
      List<String> mappingFields,
      double accuracyScore) {}

  public record SchemaValidation(
      boolean valid,
      int retryCount,
      boolean heuristicFallbackUsed,
      List<String> errors) {}

  public record PromptMetrics(
      String promptVersion,
      String trafficBucket,
      List<String> trackedMetricNames) {}

  public record SourcePreview(
      String fileName,
      String formatType,
      List<String> sheetNames,
      List<String> headers,
      List<List<String>> rows) {}

  public record MappingField(
      String sourceField,
      String canonicalField,
      Confidence confidence,
      double confidenceScore,
      boolean required,
      String coercionRule,
      String status,
      String reasoning,
      List<Alternative> alternatives,
      boolean humanReviewRequired) {}

  public record MatrixMapping(
      Integer rateColumn,
      Integer ltvHeaderRow,
      Integer priceStartCol,
      Integer dataStartRow,
      String rateFormat,
      String priceFormat) {}

  public record LlpaSection(String label, String type, int startRow) {}

  public record ProposalAudit(
      String redactedPromptHash,
      String responseHash,
      boolean externalProviderUsed,
      List<String> safetyChecks) {}

  public record MappingProposal(
      AnalysisMode mode,
      String formatType,
      Confidence confidence,
      SourcePreview sourcePreview,
      List<MappingField> mappings,
      List<String> unmappedFields,
      MatrixMapping matrix,
      List<LlpaSection> llpaSections,
      Map<String, Object> formatFingerprint,
      List<String> safetyWarnings,
      ProposalAudit audit,
      List<GoldenProfileExample> fewShotExamples,
      SchemaValidation schemaValidation,
      PromptMetrics promptMetrics) {}

  public record ProposeRequest(
      String fileName,
      String formatType,
      List<String> sheetNames,
      List<String> headers,
      List<List<String>> rows,
      AnalysisMode mode,
      String investorHint,
      String productHint,
      String promptVersion) {}

  public record PreviewRequest(JsonNode mappingConfig, List<Map<String, Object>> sampleRows) {}

  public record PreviewResponse(List<Map<String, Object>> normalizedRows, JsonNode sampleOutput, List<String> warnings) {}

  public record CreateProfileRequest(
      String name,
      String investorCode,
      String productCode,
      JsonNode mappingConfig,
      JsonNode formatFingerprint,
      JsonNode sampleOutput) {}

  public record UpdateProfileRequest(JsonNode mappingConfig, JsonNode sampleOutput) {}

  public record ProfileResponse(
      UUID profileId,
      UUID tenantId,
      String name,
      String formatType,
      String investorCode,
      String productCode,
      int version,
      String status,
      JsonNode mappingConfig,
      JsonNode formatFingerprint,
      JsonNode sampleOutput) {}

  public record ProfileListResponse(List<ProfileResponse> profiles, int count) {}

  public record GovernanceResponse(UUID profileId, String status, int version, List<String> nextActions) {}

  public record AutoMatchCandidate(UUID profileId, String name, int version, int matchScore, List<String> matchedFields, List<String> missingFields) {}

  public record DriftEvidence(List<String> missingSignatures, List<String> newSignatures, boolean updateSuggested) {}

  public record AutoMatchResponse(
      ProfileResponse profile,
      int matchScore,
      boolean autoApply,
      String fallbackReason,
      String routingAction,
      List<String> matchedFields,
      List<String> missingFields,
      List<AutoMatchCandidate> reviewMatches,
      DriftEvidence driftEvidence) {
    public AutoMatchResponse(ProfileResponse profile, int matchScore, boolean autoApply, String fallbackReason) {
      this(profile, matchScore, autoApply, fallbackReason, autoApply ? "AUTO_NORMALIZE" : "MAPPING_WIZARD", List.of(), List.of(), List.of(), new DriftEvidence(List.of(), List.of(), false));
    }
  }
}
