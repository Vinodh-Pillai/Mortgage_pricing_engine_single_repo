package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.margin.CompanyMarginPolicyService.CommandReceipt;
import com.wcpe.margin.CompanyMarginPolicyService.CreatePolicyCommand;
import com.wcpe.margin.CompanyMarginPolicyService.EffectiveWindow;
import com.wcpe.margin.CompanyMarginPolicyService.MarginPolicyVersion;
import com.wcpe.margin.CompanyMarginPolicyService.MarginRule;
import com.wcpe.margin.CompanyMarginPolicyService.MarginScope;
import com.wcpe.margin.CompanyMarginPolicyService.MarginUnit;
import com.wcpe.margin.srp.SrpCalculationService;
import com.wcpe.margin.srp.SrpInputs;
import com.wcpe.margin.srp.SrpRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SrpTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private final CompanyMarginPolicyService service = MarginServiceTestStores.companyMarginPolicyService(CLOCK);

  @Test
  void calculationSpreadTimesLoanAmount() {
    var result = new SrpCalculationService().calculate(rule(new BigDecimal("20"), new BigDecimal("0"), new BigDecimal("50")),
        new SrpInputs(new BigDecimal("6.125"), new BigDecimal("5.875"), new BigDecimal("450000"), 360, scope()));

    assertEquals(0, new BigDecimal("25.000").compareTo(result.srpBps()));
    assertEquals(new BigDecimal("0.2500"), result.srpPoints());
    assertEquals(new BigDecimal("1125.00"), result.srpDollars());
  }

  @Test
  void minMaxBoundsEnforced() {
    var low = new SrpCalculationService().calculate(rule(new BigDecimal("25"), new BigDecimal("15"), new BigDecimal("35")),
        new SrpInputs(new BigDecimal("6.000"), new BigDecimal("5.900"), new BigDecimal("450000"), 360, scope()));
    var high = new SrpCalculationService().calculate(rule(new BigDecimal("25"), new BigDecimal("15"), new BigDecimal("35")),
        new SrpInputs(new BigDecimal("6.500"), new BigDecimal("6.000"), new BigDecimal("450000"), 360, scope()));

    assertEquals(0, new BigDecimal("15").compareTo(low.srpBps()));
    assertEquals(new BigDecimal("675.00"), low.srpDollars());
    assertEquals(0, new BigDecimal("35").compareTo(high.srpBps()));
    assertEquals(new BigDecimal("1575.00"), high.srpDollars());
  }

  @Test
  void waterfallPositionAfterChannelMargin() {
    CommandReceipt created = service.createSrpDraft(command("tenant-a", "admin-a", "idem-srp-1", "hash-srp-1",
        srpVersion("srp-v1")));

    var simulation = service.simulateSrp("tenant-a", created.policyId(),
        ref -> Optional.ofNullable(Map.of(
            "cfg.srpSpreadBps", new BigDecimal("25"),
            "cfg.srpMinBps", new BigDecimal("15"),
            "cfg.srpMaxBps", new BigDecimal("35")).get(ref)),
        new BigDecimal("99.600"),
        new SrpInputs(new BigDecimal("6.125"), new BigDecimal("5.875"), new BigDecimal("450000"), 360, scope()));

    assertEquals(new BigDecimal("99.3500"), simulation.priceAfterMargin());
    assertEquals("SRP", simulation.steps().get(0).stepType());
    assertEquals("SRP-FNMA-RETAIL", simulation.steps().get(0).reasonCode());
    assertTrue(MarginReplayTestFixtures.fullStackManifest().waterfallSteps().stream()
        .map(MarginReplayService.WaterfallStep::stepType).toList().indexOf("SRP")
        > MarginReplayTestFixtures.fullStackManifest().waterfallSteps().stream()
            .map(MarginReplayService.WaterfallStep::stepType).toList().indexOf("CHANNEL_MARGIN"));
    MarginReplayService.assertRequiredWaterfallOrder(MarginReplayTestFixtures.fullStackManifest().waterfallSteps());
  }

  @Test
  void profitabilityEvidenceIncludesSrp() {
    var view = new MarginProfitabilityEvidenceController().profitabilityEvidence("tenant-a", "trace-srp", null);

    var srp = view.sections().stream().filter(section -> "srp".equals(section.sectionId())).findFirst().orElseThrow();
    assertEquals("Servicing Release Premium", srp.label());
    assertEquals("margin-service SRP policy", srp.sourceRef());
    assertTrue(srp.evidenceRefs().contains("srp-policy-version-ref-required"));
    assertTrue(view.sections().stream().map(MarginProfitabilityEvidenceController.MarginEvidenceSection::sectionId)
        .toList().indexOf("srp")
        > view.sections().stream().map(MarginProfitabilityEvidenceController.MarginEvidenceSection::sectionId)
            .toList().indexOf("channel-margin"));
  }

  private static SrpRule rule(BigDecimal spread, BigDecimal min, BigDecimal max) {
    return new SrpRule(10, "FNMA", "RETAIL", "CONVENTIONAL", spread, min, max, 4, "SRP-FNMA-RETAIL", scope());
  }

  private static CreatePolicyCommand command(String tenantId, String actorId, String idempotencyKey, String hash,
      MarginPolicyVersion version) {
    return new CreatePolicyCommand(tenantId, "request-srp", actorId, idempotencyKey, "corr-srp",
        "FNMA Retail SRP " + version.versionId(), version, hash);
  }

  private static MarginPolicyVersion srpVersion(String versionId) {
    return new MarginPolicyVersion(versionId, 1, scope(),
        new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
        List.of(new MarginRule(10, MarginUnit.BPS, "cfg.srpSpreadBps", "cfg.srpMinBps", "cfg.srpMaxBps", 4,
            "SRP-FNMA-RETAIL", scope())),
        "cfg-srp-hash-1");
  }

  private static MarginScope scope() {
    return new MarginScope("CONVENTIONAL", "FNMA", "RETAIL", "*", "*", "PURCHASE", "*");
  }
}
