package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.margin.ProfitabilityFloorService.CommandReceipt;
import com.wcpe.margin.ProfitabilityFloorService.ConfigResolver;
import com.wcpe.margin.ProfitabilityFloorService.EffectiveWindow;
import com.wcpe.margin.ProfitabilityFloorService.FloorAction;
import com.wcpe.margin.ProfitabilityFloorService.FloorBasis;
import com.wcpe.margin.ProfitabilityFloorService.FloorDecision;
import com.wcpe.margin.ProfitabilityFloorService.PolicyStatus;
import com.wcpe.margin.ProfitabilityFloorService.ProfitabilityDecision;
import com.wcpe.margin.ProfitabilityFloorService.ProfitabilityEvaluationInput;
import com.wcpe.margin.ProfitabilityFloorService.ProfitabilityFloorException;
import com.wcpe.margin.ProfitabilityFloorService.ProfitabilityPolicyVersion;
import com.wcpe.margin.ProfitabilityFloorService.ProfitabilityRule;
import com.wcpe.margin.ProfitabilityFloorService.ProfitabilityScope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfitabilityFloorServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final ProfitabilityFloorService service = MarginServiceTestStores.profitabilityFloorService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void publishBlockPolicyAndExcludesBlockedOption() {
    CommandReceipt published = publish(FloorAction.BLOCK, FloorBasis.NET_PRICE, "idem-block", "cfg.floor.netPrice");

    ProfitabilityDecision decision = service.evaluateQuote("tenant-a", scope(), resolver(Map.of(
        "cfg.floor.netPrice", new BigDecimal("99.500"))),
        input(new BigDecimal("99.200"), new BigDecimal("0.000")));

    assertEquals(PolicyStatus.PUBLISHED, published.status());
    assertEquals(FloorDecision.EXCLUDED, decision.decision());
    assertEquals(ProfitabilityFloorService.BREACH_CODE, decision.decisionCode());
    assertEquals("PROFITABILITY_FLOOR_EVALUATION", decision.waterfallSteps().get(0).stepType());
    assertEquals(2, service.outboxEvents().size());
    assertEquals(1, service.profitabilityFloorBreachTotal.get());
    assertEquals("PROFITABILITY_FLOOR_BREACHED", service.auditRecords().get(service.auditRecords().size() - 1).action());
  }

  @Test
  void warnsWhenConfiguredWithoutExcludingOption() {
    publish(FloorAction.WARN, FloorBasis.NET_PRICE, "idem-warn", "cfg.floor.warning");

    ProfitabilityDecision decision = service.evaluateQuote("tenant-a", scope(), resolver(Map.of(
        "cfg.floor.warning", new BigDecimal("99.500"))),
        input(new BigDecimal("99.200"), new BigDecimal("0.000")));

    assertEquals(FloorDecision.INCLUDED_WITH_WARNING, decision.decision());
    assertEquals(ProfitabilityFloorService.BREACH_CODE, decision.decisionCode());
    assertEquals(1, service.evaluations().size());
  }

  @Test
  void requiresExceptionWhenConfigured() {
    publish(FloorAction.REQUIRE_EXCEPTION, FloorBasis.NET_PRICE, "idem-exception", "cfg.floor.exception");

    ProfitabilityDecision decision = service.evaluateQuote("tenant-a", scope(), resolver(Map.of(
        "cfg.floor.exception", new BigDecimal("99.500"))),
        input(new BigDecimal("99.200"), new BigDecimal("0.000")));

    assertEquals(FloorDecision.NON_BINDABLE_EXCEPTION_REQUIRED, decision.decision());
    assertEquals(ProfitabilityFloorService.EXCEPTION_REQUIRED_CODE, decision.decisionCode());
    assertEquals("exception-route-risk", decision.exceptionRouteRef());
  }

  @Test
  void tieAtExactFloorPassesWithoutBreachEvent() {
    publish(FloorAction.BLOCK, FloorBasis.NET_PRICE, "idem-pass", "cfg.floor.tie");

    ProfitabilityDecision decision = service.evaluateQuote("tenant-a", scope(), resolver(Map.of(
        "cfg.floor.tie", new BigDecimal("99.500"))),
        input(new BigDecimal("99.450"), new BigDecimal("0.050")));

    assertEquals(FloorDecision.PASS, decision.decision());
    assertEquals("PROFITABILITY_FLOOR_PASS", decision.decisionCode());
    assertEquals(1, service.outboxEvents().size());
    assertEquals(0, service.profitabilityFloorBreachTotal.get());
  }

  @Test
  void rejectsMissingCostRefFailClosedForDollarProfit() {
    CommandReceipt published = publish(FloorAction.BLOCK, FloorBasis.DOLLAR_PROFIT, "idem-cost", "cfg.floor.dollars");

    ProfitabilityFloorException exception = assertThrows(ProfitabilityFloorException.class,
        () -> service.evaluateQuote("tenant-a", scope(), resolver(Map.of(
            "cfg.floor.dollars", new BigDecimal("1000.00"))),
            input(new BigDecimal("99.500"), new BigDecimal("0.000"))));

    assertFalse(published.events().isEmpty());
    assertEquals("POLICY_NOT_SATISFIED", exception.getMessage());
  }

  @Test
  void failsClosedWhenPublishedPolicyMissing() {
    ProfitabilityFloorException exception = assertThrows(ProfitabilityFloorException.class,
        () -> service.evaluateQuote("tenant-a", scope(), resolver(Map.of()),
            input(new BigDecimal("99.500"), new BigDecimal("0.000"))));

    assertEquals(ProfitabilityFloorService.POLICY_MISSING_CODE, exception.getMessage());
    assertEquals(1, service.profitabilityPolicyMissingTotal.get());
  }

  @Test
  void preventsOverlappingPublishedPolicy() {
    publish(FloorAction.BLOCK, FloorBasis.NET_PRICE, "idem-overlap-1", "cfg.floor.one");
    CommandReceipt second = service.createDraftPolicy("tenant-a", "request-1", "risk-a", "idem-overlap-2", "corr-create",
        "Retail profitability floor", version("v-overlap", FloorAction.BLOCK, FloorBasis.NET_PRICE, "cfg.floor.two"));
    service.submit("tenant-a", second.policyId(), "risk-a", "corr-submit");
    service.approve("tenant-a", second.policyId(), "approver-b", "corr-approve");

    assertEquals("PROFITABILITY_POLICY_OVERLAP", assertThrows(ProfitabilityFloorException.class,
        () -> service.publish("tenant-a", second.policyId(), "publisher-c", "corr-publish")).getMessage());
  }

  @Test
  void redactsProfitabilityWaterfallForViewerWithoutSensitivePermission() {
    publish(FloorAction.REQUIRE_EXCEPTION, FloorBasis.NET_PRICE, "idem-visibility", "cfg.floor.visibility");
    ProfitabilityDecision internal = service.evaluateQuote("tenant-a", scope(), resolver(Map.of(
        "cfg.floor.visibility", new BigDecimal("99.500"))),
        input(new BigDecimal("99.200"), new BigDecimal("0.000")));

    ProfitabilityDecision redacted = service.applyVisibility("pricing.margin.profitability.view_public", internal);

    assertEquals(internal.quoteId(), redacted.quoteId());
    assertEquals(internal.quoteOptionId(), redacted.quoteOptionId());
    assertEquals(internal.decision(), redacted.decision());
    assertEquals(internal.decisionCode(), redacted.decisionCode());
    assertNull(redacted.loadedPrice());
    assertNull(redacted.profitMetric());
    assertNull(redacted.threshold());
    assertNull(redacted.thresholdRef());
    assertNull(redacted.action());
    assertNull(redacted.exceptionRouteRef());
    assertNull(redacted.waterfallSteps().get(0).loadedPrice());
    assertNull(redacted.waterfallSteps().get(0).profitMetric());
    assertNull(redacted.waterfallSteps().get(0).threshold());
    assertEquals(1, service.profitabilityVisibilityRedactionTotal.get());
  }

  @Test
  void allowsSensitiveProfitabilityWaterfallWithPermission() {
    publish(FloorAction.BLOCK, FloorBasis.NET_PRICE, "idem-visibility-allowed", "cfg.floor.visibility.allowed");
    ProfitabilityDecision internal = service.evaluateQuote("tenant-a", scope(), resolver(Map.of(
        "cfg.floor.visibility.allowed", new BigDecimal("99.500"))),
        input(new BigDecimal("99.200"), new BigDecimal("0.000")));

    ProfitabilityDecision visible = service.applyVisibility(
        ProfitabilityFloorService.SENSITIVE_PROFITABILITY_PERMISSION, internal);

    assertEquals(new BigDecimal("99.200"), visible.loadedPrice());
    assertEquals(new BigDecimal("99.200"), visible.profitMetric());
    assertEquals(new BigDecimal("99.500"), visible.threshold());
    assertEquals(0, service.profitabilityVisibilityRedactionTotal.get());
  }

  private CommandReceipt publish(FloorAction action, FloorBasis basis, String idempotencyKey, String thresholdRef) {
    CommandReceipt created = service.createDraftPolicy("tenant-a", "request-1", "risk-a", idempotencyKey, "corr-create",
        "Retail profitability floor", version("v-" + idempotencyKey, action, basis, thresholdRef));
    service.submit("tenant-a", created.policyId(), "risk-a", "corr-submit");
    service.approve("tenant-a", created.policyId(), "approver-b", "corr-approve");
    return service.publish("tenant-a", created.policyId(), "publisher-c", "corr-publish");
  }

  private static ProfitabilityPolicyVersion version(String versionId, FloorAction action, FloorBasis basis,
      String thresholdRef) {
    return new ProfitabilityPolicyVersion(versionId, 1, scope(),
        new EffectiveWindow(NOW, null),
        List.of(new ProfitabilityRule(10, basis, thresholdRef, action,
            action == FloorAction.REQUIRE_EXCEPTION ? "exception-route-risk" : "", "PROFITABILITY_FLOOR", scope())),
        "cfg-hash-" + versionId);
  }

  private static ProfitabilityEvaluationInput input(BigDecimal priceAfterMarginsAndComp,
      BigDecimal approvedConcessionsImpact) {
    return new ProfitabilityEvaluationInput("quote-1", "quote-option-1", scope(), priceAfterMarginsAndComp,
        approvedConcessionsImpact, Optional.empty(), Optional.empty(), Optional.empty(), NOW, "corr-eval");
  }

  private static ProfitabilityScope scope() {
    return new ProfitabilityScope("CONVENTIONAL", "RETAIL", "agency", "branch-1", "LOCK_30");
  }

  private static ConfigResolver resolver(Map<String, BigDecimal> refs) {
    return ref -> Optional.ofNullable(refs.get(ref));
  }
}
