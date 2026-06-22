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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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

    ResponseStatusException exception = catchThrowableOfType(() -> controller.conditions("scenario-DSCR"),
        ResponseStatusException.class);

    assertThat(exception).isNotNull();
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void usesInjectedDurableResultStoreForStorageAndRetrieval() {
    CapturingResultStore resultStore = new CapturingResultStore();
    NonQmUnderwritingController controller = new NonQmUnderwritingController(new NonQmUnderwritingApi(), resultStore);

    UnderwritingResult evaluated = controller.evaluate(request());
    ResponseEntity<UnderwritingResult> conditions = controller.conditions(evaluated.scenarioId());
    ResponseEntity<NonQmUnderwritingApi.UnderwritingFindingsReport> findings = controller.findings(evaluated.scenarioId());

    assertThat(resultStore.saved).isEqualTo(evaluated);
    assertThat(conditions.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(conditions.getBody()).isEqualTo(evaluated);
    assertThat(findings.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(findings.getBody()).isEqualTo(evaluated.findingsReport());
  }

  private static UnderwritingRequest request() {
    return new UnderwritingRequest("tenant-1", "scenario-DSCR", NonQmProductType.DSCR,
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
        pricedContext(), Map.of(), "corr-1");
  }

  private static PricingContext pricedContext() {
    return new PricingContext(PricingStatus.PRICED, "pricing-hash-1", "nonqm-rate-sheet", 1,
        "INV-PROD-1", List.of(), List.of("nonqm-rate-sheet:v1", "nonqm-margin:v1"), Map.of("ltvBand", "70_75"));
  }

  private static final class CapturingResultStore implements UnderwritingResultStore {
    private UnderwritingResult saved;

    @Override
    public UnderwritingResult save(UnderwritingResult result) {
      saved = result;
      return result;
    }

    @Override
    public Optional<UnderwritingResult> findByScenarioId(String scenarioId) {
      return saved != null && saved.scenarioId().equals(scenarioId) ? Optional.of(saved) : Optional.empty();
    }
  }
}
