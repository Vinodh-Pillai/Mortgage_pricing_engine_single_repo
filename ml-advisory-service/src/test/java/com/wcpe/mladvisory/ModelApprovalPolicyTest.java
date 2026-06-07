package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ModelApprovalPolicyTest {
  @Test
  void shouldRejectVisibleApprovalWithoutFairnessEvidence() {
    ModelRegistryService service = ModelVersionGovernanceFixtures.registryWithSubmittedReview(false);
    String modelVersionId = service.list(AdvisoryTestFixtures.TENANT, ModelStatus.IN_REVIEW, AdvisoryType.PRICING).get(0).modelVersionId();

    MlAdvisoryResult<ModelVersionResponse> approval = service.approve(ModelVersionGovernanceFixtures.approveVisible(modelVersionId));

    assertFalse(approval.valid());
    assertEquals("ML_MODEL_EVIDENCE_INCOMPLETE", approval.errorCode().orElseThrow());
  }
}
