package com.wcpe.mladvisory;

import java.util.Set;
import java.util.stream.Collectors;

final class ModelApprovalPolicy {
  private static final Set<String> REQUIRED_VISIBLE_EVIDENCE =
      Set.of("FAIRNESS_BIAS", "VALIDATION", "SECURITY", "EXPLAINABILITY", "COMPLIANCE");

  MlAdvisoryResult<ModelVersion> validate(ModelVersion version, ApproveModelVersionCommand command) {
    if (version.allowedUse() != AllowedUse.ADVISORY_ONLY) {
      return MlAdvisoryResult.failure("ML_MODEL_ALLOWED_USE_VIOLATION");
    }
    if (version.artifactChecksum().isBlank()) {
      return MlAdvisoryResult.failure("ML_MODEL_CHECKSUM_REQUIRED");
    }
    if (version.createdBy().equals(command.actorId())) {
      return MlAdvisoryResult.failure("ML_MODEL_SELF_APPROVAL_DENIED");
    }
    if (command.targetStatus() == ModelStatus.APPROVED_ADVISORY_VISIBLE) {
      Set<String> approvedEvidence =
          version.evidence().stream()
              .filter(evidence -> "APPROVED".equals(evidence.reviewStatus()))
              .map(ModelEvidence::evidenceType)
              .collect(Collectors.toSet());
      if (!approvedEvidence.containsAll(REQUIRED_VISIBLE_EVIDENCE)) {
        return MlAdvisoryResult.failure("ML_MODEL_EVIDENCE_INCOMPLETE");
      }
    }
    return MlAdvisoryResult.success(version);
  }
}
