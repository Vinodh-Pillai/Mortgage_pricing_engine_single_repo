package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.ScenarioAnalysisApiService.RecalculationRequest;
import com.wcpe.scenarioanalysis.ScenarioAnalysisApiService.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioAnalysisApiServiceTest {
  private ScenarioAnalysisApiService service;

  @BeforeEach
  void setUp() {
    service = new ScenarioAnalysisApiService(Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void workspaceApiReturnsWrapperWithFallbackWorkspaceShape() {
    var response = service.workspace("tenant-001", "run-001", "trace-001");

    assertThat(response.tenantContext()).isEqualTo("tenant-001");
    assertThat(response.runId()).isEqualTo("run-001");
    assertThat(response.dependencyStatus()).isEqualTo("PARTIAL");
    assertThat(response.replayHash()).startsWith("sha256:");
    assertThat(response.versionRefs()).contains("scenario-analysis-service:local");
    assertThat(response.auditRefs()).contains("audit:scenario-analysis:tenant-001:run-001");
    assertThat(response.uiTraceId()).isEqualTo("trace-001");
    assertThat(response.fallbackReason()).contains("dependencies are not connected");
    assertThat((Map<String, Object>) response.data()).containsKeys(
        "dimensions", "variants", "batchGrid", "savedAnalyses", "guardrails", "exportRefs", "replayRefs");
  }

  @Test
  void recalculationApiQueuesWithoutInventingPricing() {
    var response = service.recalculate(
        "tenant-001",
        "run-001",
        new RecalculationRequest("fico", "740", List.of(Map.of("variantId", "base"))),
        "trace-002");

    assertThat(response.dependencyStatus()).isEqualTo("PARTIAL");
    assertThat(response.backendResultRefs()).contains("scenario-analysis:recalculation:queued");
    assertThat((Map<String, Object>) response.data())
        .containsEntry("status", "QUEUED")
        .containsEntry("changedDimensionId", "fico");
    assertThat(response.fallbackReason()).contains("pricing-service and eligibility-service clients are not available");
  }

  @Test
  void sensitivityApisReturnBlockedEmptyShapesWhenMetadataAndDependenciesAreUnavailable() {
    var fico = service.ficoSensitivity("tenant-001", "run-001", null);
    var ltv = service.ltvSensitivity("tenant-001", "run-001", null);
    var product = service.productComparison("tenant-001", "run-001", null);
    var lockPeriod = service.lockPeriodComparison("tenant-001", "run-001", null);

    assertThat(fico.dependencyStatus()).isEqualTo("BLOCKED");
    assertThat((Map<String, Object>) fico.data()).containsEntry("axis", "FICO").containsKey("bands");
    assertThat((Map<String, Object>) ltv.data()).containsEntry("axis", "LTV").containsKey("bands");
    assertThat((Map<String, Object>) product.data()).containsEntry("axis", "PRODUCT").containsKey("products");
    assertThat((Map<String, Object>) lockPeriod.data()).containsEntry("axis", "LOCK_PERIOD").containsKey("periods");
    assertThat(fico.replayHash()).startsWith("sha256:");
    assertThat(ltv.replayHash()).startsWith("sha256:");
    assertThat(product.replayHash()).startsWith("sha256:");
    assertThat(lockPeriod.replayHash()).startsWith("sha256:");
  }

  @Test
  void recalculateRequiresChangedDimensionId() {
    assertThatThrownBy(() -> service.recalculate(
        "tenant-001",
        "run-001",
        new RecalculationRequest(" ", "740", List.of()),
        "trace-003"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("changedDimensionId is required");
  }
}
