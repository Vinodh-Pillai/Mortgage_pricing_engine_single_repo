package com.wcpe.pricingbff.los;

import com.wcpe.pricingbff.los.LosApiModels.LosPricingRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosScenario;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class LosScenarioAdapter {
  LosScenario toScenario(LosPricingRequest request) {
    Map<String, String> facts = new LinkedHashMap<>();
    facts.put("losRequestId", request.requestId());
    facts.put("transactionType", safe(request.transactionType()));
    facts.put("requestedLoanAmount", safeValue(request.requestedLoanAmount()));
    facts.put("loanTermType", safe(request.loanTermType()));
    facts.put("amortizationType", safe(request.amortizationType()));
    facts.put("mortgageType", safe(request.mortgageType()));
    facts.put("purchasePrice", safeValue(request.purchasePrice()));
    facts.put("propertyValue", safeValue(request.propertyValue()));
    facts.put("propertyInformationType", safe(request.propertyInformationType()));
    facts.put("occupancyType", safe(request.occupancyType()));
    facts.put("numberOfUnits", safeValue(request.numberOfUnits()));
    facts.put("creditScore", safeValue(request.creditScore()));
    facts.put("incomeDocumentationType", safe(request.incomeDocumentationType()));
    facts.put("totalMonthlyIncome", safeValue(request.totalMonthlyIncome()));
    facts.put("debtToIncomeRatio", safeValue(request.debtToIncomeRatio()));
    facts.put("desiredRateLockPeriod", safeValue(request.desiredRateLockPeriod()));
    facts.put("requestSnapshotRef", requestSnapshotRef(request));
    facts.put("mappingConfigRef", mappingConfigRef(request));
    if (request.quoteAddressDTO() != null) {
      facts.put("quoteAddressDTO.state", safe(request.quoteAddressDTO().state()));
      facts.put("quoteAddressDTO.zip", safe(request.quoteAddressDTO().zip()));
      facts.put("quoteAddressDTO.countyFips", safe(request.quoteAddressDTO().countyFips()));
      facts.put("quoteAddressDTO.countyCode", safe(request.quoteAddressDTO().countyCode()));
      facts.put("quoteAddressDTO.countyName", safe(request.quoteAddressDTO().countyName()));
      facts.put("quoteAddressDTO.city", safe(request.quoteAddressDTO().city()));
      facts.put("quoteAddressDTO.street", safe(request.quoteAddressDTO().street()));
    }
    if (request.quoteBorrowerInfo() != null) {
      facts.put("quoteBorrowerInfo.borrowerLastName", safe(request.quoteBorrowerInfo().borrowerLastName()));
      facts.put("quoteBorrowerInfo.loanNumber", safe(request.quoteBorrowerInfo().loanNumber()));
      facts.put("quoteBorrowerInfo.numberOfBorrowers", safeValue(request.quoteBorrowerInfo().numberOfBorrowers()));
    }
    String scenarioId = blank(request.scenarioId()) ? null : request.scenarioId().trim();
    int scenarioVersion = scenarioId == null ? 0 : Math.max(1, request.scenarioVersion() == null ? 1 : request.scenarioVersion());
    List<Integer> lockPeriods = request.desiredRateLockPeriod() == null ? List.of() : List.of(request.desiredRateLockPeriod());
    return new LosScenario(scenarioId, request.tenantId(), scenarioVersion, facts, lockPeriods);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String requestSnapshotRef(LosPricingRequest request) {
    return "los-request-snapshot:" + safe(request.tenantId()) + ":" + safe(request.requestId());
  }

  private String mappingConfigRef(LosPricingRequest request) {
    return "los-mapping-config:" + safe(request.tenantId());
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String safeValue(Object value) {
    return value == null ? "" : value.toString();
  }
}
