package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wcpe.margin.overlay.OverlayInputs;
import com.wcpe.margin.overlay.OverlayRuleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistenceFailClosedTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void servicesWithOnlyProcessLocalStoreOfRecordFailClosedByDefault() {
    assertThrows(ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
        () -> new CompanyMarginPolicyService(CLOCK).outboxEvents());
    assertThrows(ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
        () -> new MarginReplayService(CLOCK).outboxEvents());
    assertThrows(ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
        () -> new MarginGovernanceService(CLOCK).outboxEvents());
    assertThrows(ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
        () -> new ProfitabilityFloorService(CLOCK).outboxEvents());
    assertThrows(ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
        () -> new MarginVersioningService(CLOCK).outboxEvents());
    assertThrows(ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
        () -> new BrokerCompensationService(CLOCK).outboxEvents());
    assertThrows(ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
        () -> new LoCompensationService(CLOCK).outboxEvents());
  }

  @Test
  void defaultOverlayRepositoryFailsClosedWhenNoDurableStoreExists() {
    OverlayInputs inputs = new OverlayInputs(UUID.randomUUID(), "investor", "retail", "conforming",
        "purchase", "owner", "single-family", "CA", "001", new BigDecimal("500000"), false, null, null,
        false, true, 360, Instant.parse("2026-01-01T00:00:00Z"));

    assertThrows(IllegalStateException.class, () -> OverlayRuleRepository.empty().findApplicable(inputs));
  }
}
