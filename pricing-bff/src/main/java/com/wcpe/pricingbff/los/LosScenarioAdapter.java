package com.wcpe.pricingbff.los;

import com.wcpe.pricingbff.los.LosApiModels.LosPricingRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosScenario;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class LosScenarioAdapter {
  LosScenario toScenario(LosPricingRequest request) {
    Map<String, String> facts = new LinkedHashMap<>();
    facts.put("losRequestId", request.requestId());
    facts.put("loanPurpose", safe(request.loan().loanPurpose()));
    facts.put("loanType", safe(request.loan().loanType()));
    facts.put("amortizationType", safe(request.loan().amortizationType()));
    facts.put("loanAmount", safeValue(request.loan().loanAmount()));
    facts.put("termMonths", safeValue(request.loan().termMonths()));
    if (request.loan().property() != null) {
      facts.put("propertyState", safe(request.loan().property().state()));
      facts.put("propertyCounty", safe(request.loan().property().county()));
      facts.put("propertyType", safe(request.loan().property().propertyType()));
      facts.put("occupancy", safe(request.loan().property().occupancy()));
      facts.put("units", safeValue(request.loan().property().units()));
      facts.put("purchasePrice", safeValue(request.loan().property().purchasePrice()));
      facts.put("appraisedValue", safeValue(request.loan().property().appraisedValue()));
    }
    if (!request.loan().borrowers().isEmpty()) {
      facts.put("representativeCreditScore", safeValue(request.loan().borrowers().get(0).creditScore()));
      facts.put("borrowerCount", Integer.toString(request.loan().borrowers().size()));
    }
    String scenarioId = UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.requestId()).getBytes(StandardCharsets.UTF_8)).toString();
    List<Integer> lockPeriods = request.pricing().lockPeriodDays() == null ? List.of() : List.of(request.pricing().lockPeriodDays());
    return new LosScenario(scenarioId, request.tenantId(), 1, facts, lockPeriods, request.pricing().effectiveDate());
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String safeValue(Object value) {
    return value == null ? "" : value.toString();
  }
}
