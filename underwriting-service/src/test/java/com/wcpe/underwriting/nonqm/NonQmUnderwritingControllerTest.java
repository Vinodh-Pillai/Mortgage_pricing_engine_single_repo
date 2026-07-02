package com.wcpe.underwriting.nonqm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.EligibilityOutcome;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.EligibilityStatus;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.NonQmProductType;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.PricingContext;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.PricingStatus;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.UnderwritingRequest;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.UnderwritingResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class NonQmUnderwritingControllerTest {
  @Test
  void evaluateFailsClosedWhenDurableResultPersistenceIsUnavailable() {
    NonQmUnderwritingController controller = new NonQmUnderwritingController(new NonQmUnderwritingApi());

    ResponseStatusException exception = catchThrowableOfType(() -> controller.evaluate(request()),
        ResponseStatusException.class);

    assertThat(exception).isNotNull();
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(exception.getReason()).contains("Durable underwriting result persistence is not configured");
  }

  @Test
  void retrievalFailsClosedWhenDurableResultPersistenceIsUnavailable() {
    NonQmUnderwritingController controller = new NonQmUnderwritingController(new NonQmUnderwritingApi());

    ResponseStatusException exception = catchThrowableOfType(() -> controller.conditions("tenant-1", "scenario-DSCR"),
        ResponseStatusException.class);

    assertThat(exception).isNotNull();
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void usesInjectedDurableResultStoreForStorageAndRetrieval() {
    CapturingResultStore resultStore = new CapturingResultStore();
    NonQmUnderwritingController controller = new NonQmUnderwritingController(new NonQmUnderwritingApi(), resultStore);

    UnderwritingResult evaluated = controller.evaluate(request());
    ResponseEntity<UnderwritingResult> conditions = controller.conditions(evaluated.tenantId(), evaluated.scenarioId());
    ResponseEntity<NonQmUnderwritingApi.UnderwritingFindingsReport> findings = controller.findings(evaluated.tenantId(), evaluated.scenarioId());

    assertThat(resultStore.saved).containsExactly(evaluated);
    assertThat(conditions.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(conditions.getBody()).isEqualTo(evaluated);
    assertThat(findings.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(findings.getBody()).isEqualTo(evaluated.findingsReport());
  }

  @Test
  void retrievalRequiresTenantBoundaryBeforeLookup() {
    CapturingResultStore resultStore = new CapturingResultStore();
    NonQmUnderwritingController controller = new NonQmUnderwritingController(new NonQmUnderwritingApi(), resultStore);

    ResponseStatusException exception = catchThrowableOfType(() -> controller.findings(" ", "scenario-DSCR"),
        ResponseStatusException.class);

    assertThat(exception).isNotNull();
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getReason()).isEqualTo("tenant_id is required for underwriting result lookup");
    assertThat(resultStore.lookupCount).isZero();
  }

  @Test
  void retrievalDoesNotLeakSameScenarioAcrossTenants() {
    CapturingResultStore resultStore = new CapturingResultStore();
    NonQmUnderwritingController controller = new NonQmUnderwritingController(new NonQmUnderwritingApi(), resultStore);

    UnderwritingResult tenantOne = controller.evaluate(request("tenant-1", "scenario-DSCR", "corr-tenant-1"));
    controller.evaluate(request("tenant-2", "scenario-DSCR", "corr-tenant-2"));

    ResponseEntity<UnderwritingResult> conditions = controller.conditions("tenant-1", tenantOne.scenarioId());
    ResponseEntity<UnderwritingResult> missing = controller.conditions("tenant-3", tenantOne.scenarioId());

    assertThat(conditions.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(conditions.getBody()).isEqualTo(tenantOne);
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deployableSpringWiringUsesAutowiredDurableStoreConstructor() {
    assertThat(Arrays.stream(NonQmUnderwritingController.class.getDeclaredConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
        .anyMatch(constructor -> Arrays.equals(constructor.getParameterTypes(), new Class<?>[] {
            NonQmUnderwritingApi.class, UnderwritingResultStore.class
        }))).isTrue();
  }

  private static UnderwritingRequest request() {
    return request("tenant-1", "scenario-DSCR", "corr-1");
  }

  private static UnderwritingRequest request(String tenantId, String scenarioId, String correlationId) {
    return new UnderwritingRequest(tenantId, scenarioId, NonQmProductType.DSCR,
        "NONQM-DSCR", "INV-A", "BROKER", Instant.parse("2026-06-13T00:00:00Z"), Map.of(
        "nonQm.dscr.ratio", "1.18",
        "income.rental.evidenceRef", "doc:rental:1",
        "property.taxInsurance.evidenceRef", "doc:pitia:1",
        "credit.fico", "742",
        "credit.tradelines", "3",
        "credit.housingHistory", "0x30",
        "property.appraisalRef", "appraisal:1",
        "property.condition", "C3",
        "property.type", "SFR"),
        new EligibilityOutcome(EligibilityStatus.ELIGIBLE, "eligibility:nonqm:passed", "NON_QM_ELIGIBLE", List.of()),
        pricedContext(), Map.of(), correlationId);
  }

  private static PricingContext pricedContext() {
    return new PricingContext(PricingStatus.PRICED, "pricing-hash-1", "nonqm-rate-sheet", 1,
        "INV-PROD-1", List.of(), List.of("nonqm-rate-sheet:v1", "nonqm-margin:v1"), Map.of("ltvBand", "70_75"));
  }

  private static final class CapturingResultStore implements UnderwritingResultStore {
    private final List<UnderwritingResult> saved = new ArrayList<>();
    private int lookupCount;

    @Override
    public UnderwritingResult save(UnderwritingResult result) {
      saved.removeIf(existing -> existing.tenantId().equals(result.tenantId()) && existing.scenarioId().equals(result.scenarioId()));
      saved.add(result);
      return result;
    }

    @Override
    public Optional<UnderwritingResult> findByScenarioId(String tenantId, String scenarioId) {
      lookupCount++;
      return saved.stream()
          .filter(result -> result.tenantId().equals(tenantId) && result.scenarioId().equals(scenarioId))
          .findFirst();
    }
  }
}
