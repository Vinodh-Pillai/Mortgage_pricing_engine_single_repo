package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class WorkbenchMlAdvisoryApiControllerContractTest {
  @Test
  void exposesWorkbenchMlAdvisoryRoot() {
    RestController restController = WorkbenchMlAdvisoryApiController.class.getAnnotation(RestController.class);
    RequestMapping mapping = WorkbenchMlAdvisoryApiController.class.getAnnotation(RequestMapping.class);

    assertNotNull(restController);
    assertEquals("/api/v1/ml-advisory", mapping.value()[0]);
  }

  @Test
  void insightsReturnRecommendationsWithGovernanceMetadata() throws NoSuchMethodException {
    Method method = WorkbenchMlAdvisoryApiController.class.getDeclaredMethod("insights", String.class);
    WorkbenchMlAdvisoryApiController.AdvisoryResponse<WorkbenchMlAdvisoryApiController.InsightsData> response =
        new WorkbenchMlAdvisoryApiController().insights("trace-pii-24-s36");

    assertEquals("/insights", method.getAnnotation(GetMapping.class).value()[0]);
    assertEquals("trace-pii-24-s36", response.uiTraceId());
    assertEquals("PARTIAL", response.dependencyStatus());
    assertFalse(response.auditRefs().isEmpty());
    assertFalse(response.replayHash().isBlank());
    assertTrue(response.versionRefs().contains("PII-24-S36"));
    assertTrue(response.data().advisoryUnavailable());
    assertEquals("VIEW_EXPLANATION", response.data().recommendations().get(0).allowedActions().get(0));
    assertFalse(response.data().recommendations().get(0).automaticDecisionApplied());
  }

  @Test
  void exposesModelGovernanceLifecycleAndCompatibilityRoutes() throws NoSuchMethodException {
    assertEquals("/models", WorkbenchMlAdvisoryApiController.class.getDeclaredMethod("models", String.class).getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/models", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("registerModel", String.class, WorkbenchMlAdvisoryApiController.ModelRegistrationRequest.class)
        .getAnnotation(PostMapping.class).value()[0]);
    assertEquals("/models/{modelId}/versions/{version}/approval", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("approval", String.class, String.class, String.class)
        .getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/models/{modelId}/versions/{version}/approval", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("approve", String.class, String.class, String.class, WorkbenchMlAdvisoryApiController.ApprovalDecisionRequest.class)
        .getAnnotation(PostMapping.class).value()[0]);
    assertEquals("/models/{modelId}/versions/{version}/promote", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("promote", String.class, String.class, String.class, WorkbenchMlAdvisoryApiController.PromotionRequest.class)
        .getAnnotation(PostMapping.class).value()[0]);
    assertEquals("/models/compatibility", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("compatibility", String.class)
        .getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/models/compatibility/check", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("compatibilityCheck", String.class)
        .getAnnotation(PostMapping.class).value()[0]);
  }

  @Test
  void exposesDriftAlertAndFeedbackRoutes() throws NoSuchMethodException {
    assertEquals("/drift/feature", WorkbenchMlAdvisoryApiController.class.getDeclaredMethod("featureDrift", String.class).getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/drift/prediction", WorkbenchMlAdvisoryApiController.class.getDeclaredMethod("predictionDrift", String.class).getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/drift/population", WorkbenchMlAdvisoryApiController.class.getDeclaredMethod("populationDrift", String.class).getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/drift/alerts", WorkbenchMlAdvisoryApiController.class.getDeclaredMethod("alerts", String.class).getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/drift/alerts/{alertId}/acknowledge", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("acknowledgeAlert", String.class, String.class)
        .getAnnotation(PostMapping.class).value()[0]);
    assertEquals("/drift/alerts/{alertId}/resolve", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("resolveAlert", String.class, String.class)
        .getAnnotation(PostMapping.class).value()[0]);
    assertEquals("/drift/alerts/{alertId}/suppress", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("suppressAlert", String.class, String.class)
        .getAnnotation(PostMapping.class).value()[0]);
    assertEquals("/feedback", WorkbenchMlAdvisoryApiController.class
        .getDeclaredMethod("feedback", String.class, WorkbenchMlAdvisoryApiController.FeedbackRequest.class)
        .getAnnotation(PostMapping.class).value()[0]);
  }

  @Test
  void approvalDoesNotDefaultMissingDecisionToApproved() {
    WorkbenchMlAdvisoryApiController.AdvisoryResponse<WorkbenchMlAdvisoryApiController.ModelApprovalData> response =
        new WorkbenchMlAdvisoryApiController().approve("model-1", "v1", "trace-approval", null);

    assertEquals("PENDING_REVIEW", response.data().status());
    assertEquals("REVIEW_REQUIRED", response.data().decision());
    assertTrue(response.events().contains("MlModelApprovalReviewRequired.v1"));
    assertTrue(response.fallbackReason().contains("Explicit approval evidence"));
  }

  @Test
  void approvalAcceptsExplicitApprovedDecisionOnly() {
    WorkbenchMlAdvisoryApiController.AdvisoryResponse<WorkbenchMlAdvisoryApiController.ModelApprovalData> response =
        new WorkbenchMlAdvisoryApiController()
            .approve("model-1", "v1", "trace-approval", new WorkbenchMlAdvisoryApiController.ApprovalDecisionRequest("approver-1", "APPROVED", "reviewed"));

    assertEquals("APPROVED", response.data().status());
    assertEquals("APPROVED", response.data().decision());
    assertTrue(response.events().contains("MlModelVersionApproved.v1"));
  }

  @Test
  void promoteSucceedsWhenLocalPrerequisitesAreSupplied() {
    WorkbenchMlAdvisoryApiController.AdvisoryResponse<WorkbenchMlAdvisoryApiController.ModelMutationData> response =
        new WorkbenchMlAdvisoryApiController()
            .promote("model-1", "v1", "trace-promote", new WorkbenchMlAdvisoryApiController.PromotionRequest(true, true, true));

    assertEquals("PROMOTED", response.data().status());
    assertTrue(response.data().message().contains("prerequisites were supplied"));
    assertTrue(response.events().contains("MlModelVersionPromoted.v1"));
  }

  @Test
  void promoteBlocksWhenLocalPrerequisitesAreMissing() {
    WorkbenchMlAdvisoryApiController.AdvisoryResponse<WorkbenchMlAdvisoryApiController.ModelMutationData> response =
        new WorkbenchMlAdvisoryApiController().promote("model-1", "v1", "trace-promote", null);

    assertEquals("PROMOTION_BLOCKED", response.data().status());
    assertTrue(response.events().contains("MlModelVersionPromotionBlocked.v1"));
  }

  @Test
  void cacheMetadataIsExposedForInsightsRegistryDriftAndAlerts() {
    WorkbenchMlAdvisoryApiController controller = new WorkbenchMlAdvisoryApiController();

    assertEquals("PT5M", controller.insights("trace-insights").cacheMetadata().get(0).ttl());
    assertEquals("PT10M", controller.models("trace-models").cacheMetadata().get(0).ttl());
    assertEquals("PT1M", controller.featureDrift("trace-drift").cacheMetadata().get(0).ttl());
    assertEquals("PT30S", controller.alerts("trace-alerts").cacheMetadata().get(0).ttl());
    assertFalse(controller.insights("trace-insights").cacheMetadata().get(0).cacheAvailable());
  }

  @Test
  void feedbackLinksToRecommendationAndDoesNotRequireRetrainingInfrastructure() {
    WorkbenchMlAdvisoryApiController.AdvisoryResponse<WorkbenchMlAdvisoryApiController.FeedbackData> response =
        new WorkbenchMlAdvisoryApiController()
            .feedback(
                "trace-feedback",
                new WorkbenchMlAdvisoryApiController.FeedbackRequest(
                    "recommendation-123", 4, "useful", "ACCEPTED", Map.of("note", "reviewed")));

    assertEquals("recommendation-123", response.data().recommendationId());
    assertEquals(4, response.data().rating());
    assertEquals("ACCEPTED", response.data().outcome());
    assertTrue(response.data().message().contains("outside this story scope"));
    assertTrue(response.events().contains("MlAdvisoryFeedbackCaptured.v1"));
  }
}
