package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wcpe.margin.CompanyMarginPolicyService.CommandReceipt;
import com.wcpe.margin.CompanyMarginPolicyService.CreatePolicyCommand;
import com.wcpe.margin.CompanyMarginPolicyService.EffectiveWindow;
import com.wcpe.margin.CompanyMarginPolicyService.MarginPolicyVersion;
import com.wcpe.margin.CompanyMarginPolicyService.MarginRule;
import com.wcpe.margin.CompanyMarginPolicyService.MarginScope;
import com.wcpe.margin.CompanyMarginPolicyService.MarginUnit;
import com.wcpe.margin.overlay.OverlayInputs;
import com.wcpe.margin.overlay.OverlayPolicyType;
import com.wcpe.margin.overlay.OverlayRule;
import com.wcpe.margin.overlay.OverlayRuleRepository.InMemoryOverlayRuleRepository;
import com.wcpe.margin.srp.SrpCalculationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OverlayTest {
  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000134");
  private static final UUID RULE_BOOK_ID = UUID.fromString("20000000-0000-0000-0000-000000000134");

  @Test
  void jumboMarginAdjustment() {
    CompanyMarginPolicyService service = service(List.of(rule("50.000", "25.000", "75.000", 10, null)));
    CommandReceipt created = service.createOverlayDraft(command("tenant-overlay", version("v-overlay-1")));

    var simulation = service.simulateOverlay(TENANT_ID.toString(), created.policyId(), ref -> Optional.empty(),
        new BigDecimal("99.750"), inputs(true));

    assertEquals(new BigDecimal("99.250"), simulation.priceAfterMargin());
    assertEquals("MARGIN_COMPONENT", simulation.steps().get(0).stepType());
    assertEquals("JUMBO_MARGIN_OVERLAY", simulation.steps().get(0).reasonCode());
  }

  @Test
  void minMaxBoundsEnforced() {
    CompanyMarginPolicyService service = service(List.of(rule("90.000", "25.000", "75.000", 10, null)));
    CommandReceipt created = service.createOverlayDraft(command("tenant-overlay-bounds", version("v-overlay-2")));

    var simulation = service.simulateOverlay(TENANT_ID.toString(), created.policyId(), ref -> Optional.empty(),
        new BigDecimal("99.750"), inputs(true));

    assertEquals(new BigDecimal("99.000"), simulation.priceAfterMargin());
  }

  @Test
  void exclusivityGroupEnforced() {
    CompanyMarginPolicyService service = service(List.of(
        rule("25.000", "25.000", "75.000", 5, "jumbo-family"),
        rule("50.000", "25.000", "75.000", 10, "jumbo-family")));
    CommandReceipt created = service.createOverlayDraft(command("tenant-overlay-exclusive", version("v-overlay-3")));

    var simulation = service.simulateOverlay(TENANT_ID.toString(), created.policyId(), ref -> Optional.empty(),
        new BigDecimal("99.750"), inputs(true));

    assertEquals(1, simulation.steps().size());
    assertEquals(new BigDecimal("99.250"), simulation.priceAfterMargin());
  }

  @Test
  void fullWaterfallWithOverlays() {
    CompanyMarginPolicyService service = service(List.of(rule("50.000", "25.000", "75.000", 10, null)));
    CommandReceipt company = service.createDraft(command("company", version("v-company")));
    CommandReceipt channel = service.createChannelDraft(command("channel", channelVersion("v-channel")));
    CommandReceipt overlay = service.createOverlayDraft(command("overlay", version("v-overlay-4")));
    Map<String, BigDecimal> config = Map.of(
        "cfg.companyMarginBps", new BigDecimal("25"), "cfg.companyMarginMinBps", new BigDecimal("10"), "cfg.companyMarginMaxBps", new BigDecimal("40"),
        "cfg.channelMarginBps", new BigDecimal("15"), "cfg.channelMarginMinBps", new BigDecimal("10"), "cfg.channelMarginMaxBps", new BigDecimal("20"));

    var companyResult = service.simulate(TENANT_ID.toString(), company.policyId(), ref -> Optional.ofNullable(config.get(ref)), new BigDecimal("100.000"));
    var channelResult = service.simulateChannel(TENANT_ID.toString(), channel.policyId(), ref -> Optional.ofNullable(config.get(ref)),
        companyResult.priceAfterMargin(), scope());
    var overlayResult = service.simulateOverlay(TENANT_ID.toString(), overlay.policyId(), ref -> Optional.empty(),
        channelResult.priceAfterMargin(), inputs(true));

    assertEquals("COMPANY_MARGIN", companyResult.steps().get(0).stepType());
    assertEquals("CHANNEL_MARGIN", channelResult.steps().get(0).stepType());
    assertEquals("MARGIN_COMPONENT", overlayResult.steps().get(0).stepType());
    assertEquals(new BigDecimal("99.100"), overlayResult.priceAfterMargin());
  }

  private static CompanyMarginPolicyService service(List<OverlayRule> rules) {
    return new CompanyMarginPolicyService(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
        new SrpCalculationService(), new InMemoryOverlayRuleRepository(rules));
  }

  private static CreatePolicyCommand command(String idempotencyKey, MarginPolicyVersion version) {
    return new CreatePolicyCommand(TENANT_ID.toString(), "request-1", "admin-a", idempotencyKey, "corr-1",
        "Overlay Policy " + version.versionId(), version, "hash-" + idempotencyKey);
  }

  private static MarginPolicyVersion version(String versionId) {
    return new MarginPolicyVersion(versionId, 1, scope(), new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
        List.of(new MarginRule(1, MarginUnit.BPS, "cfg.companyMarginBps", "cfg.companyMarginMinBps", "cfg.companyMarginMaxBps", 3, "COMPANY_MARGIN")), "cfg-hash");
  }

  private static MarginPolicyVersion channelVersion(String versionId) {
    return new MarginPolicyVersion(versionId, 1, scope(), new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
        List.of(new MarginRule(1, MarginUnit.BPS, "cfg.channelMarginBps", "cfg.channelMarginMinBps", "cfg.channelMarginMaxBps", 3, "CHANNEL_MARGIN", scope())), "cfg-channel-hash");
  }

  private static OverlayInputs inputs(boolean jumbo) {
    return new OverlayInputs(TENANT_ID, "FNMA", "RETAIL", "CONVENTIONAL", "PURCHASE", "PRIMARY", "SFR",
        "CA", "06037", new BigDecimal("900000"), false, null, null, false, jumbo, 360,
        Instant.parse("2026-06-13T00:00:00Z"));
  }

  private static OverlayRule rule(String bps, String min, String max, int priority, String exclusivityGroup) {
    return new OverlayRule(TENANT_ID, RULE_BOOK_ID, UUID.randomUUID(), OverlayPolicyType.JUMBO, "FNMA", "RETAIL", "CONVENTIONAL",
        "PURCHASE", "PRIMARY", "SFR", "CA", "06037", new BigDecimal(bps), new BigDecimal(min), new BigDecimal(max),
        priority, exclusivityGroup, "JUMBO_MARGIN_OVERLAY", Instant.parse("2026-01-01T00:00:00Z"), null, true);
  }

  private static MarginScope scope() {
    return new MarginScope("CONVENTIONAL", "FNMA", "RETAIL", "CA", "PRIMARY", "PURCHASE", "*");
  }
}
