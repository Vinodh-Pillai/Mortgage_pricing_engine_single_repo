package com.wcpe.mladvisory;

import com.wcpe.mladvisory.FairLendingAnalysisService.FairLendingEvent;
import com.wcpe.mladvisory.FairLendingAnalysisService.FairLendingEventRepository;
import com.wcpe.mladvisory.FairLendingAnalysisService.FairLendingOutcomeRepository;
import com.wcpe.mladvisory.FairLendingAnalysisService.PricingOutcome;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class TestFairLendingOutcomeRepository implements FairLendingOutcomeRepository {
  private final List<PricingOutcome> outcomes = new ArrayList<>();

  @Override
  public void save(PricingOutcome outcome) {
    outcomes.add(Objects.requireNonNull(outcome));
  }

  @Override
  public List<PricingOutcome> findByTenantAndDateRange(UUID tenantId, LocalDate startDate, LocalDate endDate) {
    return outcomes.stream()
        .filter(outcome -> outcome.tenantId().equals(tenantId))
        .filter(outcome -> {
          LocalDate date = outcome.pricingDate().atZone(ZoneOffset.UTC).toLocalDate();
          return (date.isEqual(startDate) || date.isAfter(startDate)) && (date.isEqual(endDate) || date.isBefore(endDate));
        })
        .toList();
  }
}

final class TestFairLendingEventRepository implements FairLendingEventRepository {
  private final List<FairLendingEvent> events = new ArrayList<>();

  @Override
  public void save(FairLendingEvent event) {
    events.add(Objects.requireNonNull(event));
  }

  @Override
  public List<FairLendingEvent> findAll() {
    return List.copyOf(events);
  }
}
