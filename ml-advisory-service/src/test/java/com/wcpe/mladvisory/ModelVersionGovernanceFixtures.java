package com.wcpe.mladvisory;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

final class ModelVersionGovernanceFixtures {
  private ModelVersionGovernanceFixtures() {}

  static ModelRegistryService registryWithSubmittedReview(boolean completeEvidence) {
    ModelRegistryService service = new ModelRegistryService(Clock.fixed(AdvisoryTestFixtures.NOW, ZoneOffset.UTC), new TestModelVersionRepository());
    ModelVersionResponse registered =
        service.register(registerCommand("sha256:approved", AllowedUse.ADVISORY_ONLY, completeEvidence)).value().orElseThrow();
    service.submitReview(AdvisoryTestFixtures.TENANT, registered.modelVersionId(), "ml-operator-2", "corr-submit-review");
    return service;
  }

  static ModelRegistryService registryWithVisibleApproval() {
    ModelRegistryService service = registryWithSubmittedReview(true);
    String modelVersionId = service.list(AdvisoryTestFixtures.TENANT, ModelStatus.IN_REVIEW, AdvisoryType.PRICING).get(0).modelVersionId();
    service.approve(approveVisible(modelVersionId));
    return service;
  }

  static RegisterModelVersionCommand registerCommand(String checksum, AllowedUse allowedUse, boolean completeEvidence) {
    return new RegisterModelVersionCommand(
        AdvisoryTestFixtures.TENANT,
        "idem-model-version-" + (checksum == null ? "missing" : checksum.replace(':', '-')) + "-" + completeEvidence,
        "ml-operator-1",
        "pricing-advisory-model",
        completeEvidence ? "1.0.0" : "1.0.1",
        List.of(AdvisoryType.PRICING),
        allowedUse,
        "local://models/ml-advisory/pricing-advisory-model.fake",
        checksum,
        "ml-advisory-feature-schema-v1",
        "ml-platform",
        completeEvidence ? completeEvidence() : List.of(new ModelEvidence("VALIDATION", "evidence://validation", Map.of(), "APPROVED")),
        Map.of("trainingData", "governed-dataset-ref"),
        "corr-register-model-version");
  }

  static ApproveModelVersionCommand approveVisible(String modelVersionId) {
    return new ApproveModelVersionCommand(
        AdvisoryTestFixtures.TENANT,
        modelVersionId,
        "risk-admin-1",
        ModelStatus.APPROVED_ADVISORY_VISIBLE,
        "MRM-APPROVE-1",
        "visible advisory approval",
        "corr-approve-model-version");
  }

  private static List<ModelEvidence> completeEvidence() {
    return List.of(
        new ModelEvidence("FAIRNESS_BIAS", "evidence://fairness", Map.of(), "APPROVED"),
        new ModelEvidence("VALIDATION", "evidence://validation", Map.of(), "APPROVED"),
        new ModelEvidence("SECURITY", "evidence://security", Map.of(), "APPROVED"),
        new ModelEvidence("EXPLAINABILITY", "evidence://explainability", Map.of(), "APPROVED"),
        new ModelEvidence("COMPLIANCE", "evidence://compliance", Map.of(), "APPROVED"));
  }
}
