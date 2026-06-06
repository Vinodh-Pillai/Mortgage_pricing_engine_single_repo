package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.margin.CompanyMarginPolicyService.CommandReceipt;
import com.wcpe.margin.CompanyMarginPolicyService.CreatePolicyCommand;
import com.wcpe.margin.CompanyMarginPolicyService.EffectiveWindow;
import com.wcpe.margin.CompanyMarginPolicyService.BranchOverlayContext;
import com.wcpe.margin.CompanyMarginPolicyService.MarginPolicyException;
import com.wcpe.margin.CompanyMarginPolicyService.MarginPolicyVersion;
import com.wcpe.margin.CompanyMarginPolicyService.MarginRule;
import com.wcpe.margin.CompanyMarginPolicyService.MarginScope;
import com.wcpe.margin.CompanyMarginPolicyService.MarginUnit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompanyMarginPolicyServiceTest {
  private final CompanyMarginPolicyService service = new CompanyMarginPolicyService(
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void createSimulateApproveAndPublishCompanyMarginPolicy() {
    CommandReceipt created = service.createDraft(command("tenant-a", "admin-a", "idem-1", "hash-1", version("v1")));

    assertEquals(CompanyMarginPolicyService.PolicyStatus.DRAFT, created.status());
    assertEquals(1, service.auditRecords().size());

    service.submit("tenant-a", created.policyId(), "admin-a", "corr-2");
    service.approve("tenant-a", created.policyId(), "approver-b", "corr-3");
    CommandReceipt published = service.publish("tenant-a", created.policyId(), "publisher-c", "corr-4");

    var simulation = service.simulate("tenant-a", created.policyId(),
        ref -> Optional.ofNullable(Map.of(
            "cfg.companyMarginBps", new BigDecimal("25"),
            "cfg.companyMarginMinBps", new BigDecimal("10"),
            "cfg.companyMarginMaxBps", new BigDecimal("40")).get(ref)),
        new BigDecimal("100.000"));

    assertEquals(CompanyMarginPolicyService.PolicyStatus.PUBLISHED, published.status());
    assertEquals(1, service.outboxEvents().size());
    assertEquals("COMPANY", service.outboxEvents().get(0).policyType());
    assertEquals(new BigDecimal("99.750"), simulation.priceAfterMargin());
    assertEquals("COMPANY_MARGIN", simulation.steps().get(0).stepType());
    assertTrue(service.resolvePublished("tenant-a", scope(), Instant.parse("2026-01-02T00:00:00Z")).isPresent());
  }

  @Test
  void rejectsMissingConfigRefFailClosed() {
    CommandReceipt created = service.createDraft(command("tenant-a", "admin-a", "idem-2", "hash-2", version("v2")));

    MarginPolicyException exception = assertThrows(MarginPolicyException.class,
        () -> service.simulate("tenant-a", created.policyId(), ref -> Optional.empty(), new BigDecimal("100.000")));

    assertEquals("MARGIN_CONFIG_UNAVAILABLE", exception.getMessage());
  }

  @Test
  void rejectsSelfApprovalAndIdempotencyConflict() {
    CommandReceipt created = service.createDraft(command("tenant-a", "admin-a", "idem-3", "hash-3", version("v3")));
    service.submit("tenant-a", created.policyId(), "admin-a", "corr-2");

    assertEquals("MARGIN_APPROVAL_SOD_VIOLATION", assertThrows(MarginPolicyException.class,
        () -> service.approve("tenant-a", created.policyId(), "admin-a", "corr-3")).getMessage());
    assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(MarginPolicyException.class,
        () -> service.createDraft(command("tenant-a", "admin-a", "idem-3", "different", version("v4")))).getMessage());
  }

  @Test
  void rejectsOverlappingPublishedScopeWithinTenantOnly() {
    CommandReceipt first = publish(command("tenant-a", "admin-a", "idem-4", "hash-4", version("v5")), "approver-b");
    assertFalse(first.events().isEmpty());

    CommandReceipt second = service.createDraft(command("tenant-a", "admin-c", "idem-5", "hash-5", version("v6")));
    service.submit("tenant-a", second.policyId(), "admin-c", "corr-2");
    service.approve("tenant-a", second.policyId(), "approver-d", "corr-3");

    assertEquals("MARGIN_SCOPE_OVERLAP", assertThrows(MarginPolicyException.class,
        () -> service.publish("tenant-a", second.policyId(), "publisher-e", "corr-4")).getMessage());
  }

  @Test
  void channelMarginResolverSelectsMostSpecificRuleAndPublishesChannelEvent() {
    CommandReceipt created = service.createChannelDraft(command("tenant-a", "admin-a", "idem-6", "hash-6",
        channelVersion("v-channel-1", List.of(
            new MarginRule(20, MarginUnit.BPS, "cfg.channelRetailBps", "cfg.channelRetailMinBps",
                "cfg.channelRetailMaxBps", 3, "CHANNEL_MARGIN", new MarginScope("*", "*", "RETAIL", "*", "*", "*", "*")),
            new MarginRule(10, MarginUnit.BPS, "cfg.channelRetailPurchaseBps", "cfg.channelRetailPurchaseMinBps",
                "cfg.channelRetailPurchaseMaxBps", 3, "CHANNEL_MARGIN", scope())))));

    service.submit("tenant-a", created.policyId(), "admin-a", "corr-2");
    service.approve("tenant-a", created.policyId(), "approver-b", "corr-3");
    service.publish("tenant-a", created.policyId(), "publisher-c", "corr-4");

    var simulation = service.simulateChannel("tenant-a", created.policyId(),
        ref -> Optional.ofNullable(Map.of(
            "cfg.channelRetailBps", new BigDecimal("30"),
            "cfg.channelRetailMinBps", new BigDecimal("5"),
            "cfg.channelRetailMaxBps", new BigDecimal("40"),
            "cfg.channelRetailPurchaseBps", new BigDecimal("15"),
            "cfg.channelRetailPurchaseMinBps", new BigDecimal("10"),
            "cfg.channelRetailPurchaseMaxBps", new BigDecimal("20")).get(ref)),
        new BigDecimal("99.750"), scope());

    assertEquals("CHANNEL", service.outboxEvents().get(0).policyType());
    assertEquals(new BigDecimal("99.600"), simulation.priceAfterMargin());
    assertEquals("CHANNEL_MARGIN", simulation.steps().get(0).stepType());
    assertEquals("CHANNEL_MARGIN", simulation.steps().get(0).reasonCode());
  }

  @Test
  void channelMarginResolverFailsClosedForMissingOrAmbiguousRule() {
    CommandReceipt missing = service.createChannelDraft(command("tenant-a", "admin-a", "idem-7", "hash-7",
        channelVersion("v-channel-2", List.of(new MarginRule(10, MarginUnit.BPS, "cfg.channelRetailBps",
            "cfg.channelRetailMinBps", "cfg.channelRetailMaxBps", 3, "CHANNEL_MARGIN",
            new MarginScope("*", "*", "RETAIL", "*", "*", "*", "*"))))));

    assertEquals("CHANNEL_NOT_CONFIGURED", assertThrows(MarginPolicyException.class,
        () -> service.simulateChannel("tenant-a", missing.policyId(), ref -> Optional.empty(), new BigDecimal("99.750"),
            new MarginScope("CONVENTIONAL", "*", "WHOLESALE", "*", "*", "PURCHASE", "*"))).getMessage());

    CommandReceipt ambiguous = service.createChannelDraft(command("tenant-a", "admin-a", "idem-8", "hash-8",
        channelVersion("v-channel-3", List.of(
            new MarginRule(10, MarginUnit.BPS, "cfg.channelRetailBps", "cfg.channelRetailMinBps",
                "cfg.channelRetailMaxBps", 3, "CHANNEL_MARGIN", scope()),
            new MarginRule(10, MarginUnit.BPS, "cfg.channelRetailAltBps", "cfg.channelRetailAltMinBps",
                "cfg.channelRetailAltMaxBps", 3, "CHANNEL_MARGIN", scope())))));

    assertEquals("CHANNEL_MARGIN_OVERLAP", assertThrows(MarginPolicyException.class,
        () -> service.simulateChannel("tenant-a", ambiguous.policyId(), ref -> Optional.of(BigDecimal.ONE),
            new BigDecimal("99.750"), scope())).getMessage());
  }

  @Test
  void branchOverlayResolverAppliesNearestAncestorWhenConfigured() {
    CommandReceipt created = service.createBranchOverlayDraft(command("tenant-a", "admin-a", "idem-9", "hash-9",
        branchVersion("v-branch-1", List.of(
            branchRule(20, "cfg.parentOverlayBps", "branch-parent", "region-east"),
            branchRule(10, "cfg.childOverlayBps", "branch-child", "region-east")))));

    service.submit("tenant-a", created.policyId(), "admin-a", "corr-2");
    service.approve("tenant-a", created.policyId(), "approver-b", "corr-3");
    service.publish("tenant-a", created.policyId(), "publisher-c", "corr-4");

    var simulation = service.simulateBranchOverlay("tenant-a", created.policyId(),
        ref -> Optional.ofNullable(Map.of(
            "cfg.parentOverlayBps", new BigDecimal("12"),
            "cfg.childOverlayBps", new BigDecimal("18"),
            "cfg.branchMinBps", new BigDecimal("1"),
            "cfg.branchMaxBps", new BigDecimal("25"),
            "cfg.enterpriseLimitBps", new BigDecimal("20")).get(ref)),
        new BigDecimal("99.600"), branchContext("branch-grandchild", List.of("branch-child", "branch-parent"), false));

    assertEquals("BRANCH_OVERLAY", service.outboxEvents().get(0).policyType());
    assertEquals("BRANCH_OVERLAY_POLICY_PUBLISHED", service.auditRecords().get(service.auditRecords().size() - 1).action());
    assertEquals(new BigDecimal("99.420"), simulation.priceAfterMargin());
    assertEquals("BRANCH_OVERLAY", simulation.steps().get(0).stepType());
  }

  @Test
  void branchLimitEvaluatorRejectsOverlayBeyondConfiguredLimit() {
    CommandReceipt created = service.createBranchOverlayDraft(command("tenant-a", "admin-a", "idem-10", "hash-10",
        branchVersion("v-branch-2", List.of(branchRule(10, "cfg.childOverlayBps", "branch-child", "region-east")))));

    assertEquals("BRANCH_OVERLAY_LIMIT_EXCEEDED", assertThrows(MarginPolicyException.class,
        () -> service.simulateBranchOverlay("tenant-a", created.policyId(),
            ref -> Optional.ofNullable(Map.of(
                "cfg.childOverlayBps", new BigDecimal("30"),
                "cfg.branchMinBps", new BigDecimal("1"),
                "cfg.branchMaxBps", new BigDecimal("40"),
                "cfg.enterpriseLimitBps", new BigDecimal("20")).get(ref)),
            new BigDecimal("99.600"), branchContext("branch-child", List.of("branch-parent"), false))).getMessage());
  }

  @Test
  void branchOverlayResolverFailsOnStaleHierarchyAndUnauthorizedBranch() {
    CommandReceipt created = service.createBranchOverlayDraft(command("tenant-a", "admin-a", "idem-11", "hash-11",
        branchVersion("v-branch-3", List.of(branchRule(10, "cfg.childOverlayBps", "branch-child", "region-east")))));

    assertEquals("BRANCH_HIERARCHY_STALE", assertThrows(MarginPolicyException.class,
        () -> service.simulateBranchOverlay("tenant-a", created.policyId(), ref -> Optional.of(BigDecimal.ONE),
            new BigDecimal("99.600"), branchContext("branch-child", List.of(), true))).getMessage());

    assertEquals("BRANCH_SCOPE_UNAUTHORIZED", assertThrows(MarginPolicyException.class,
        () -> service.simulateBranchOverlay("tenant-a", created.policyId(), ref -> Optional.of(BigDecimal.ONE),
            new BigDecimal("99.600"), new BranchOverlayContext("tenant-a", "branch-other", "region-east", List.of(),
                "hierarchy-v1", "cfg.enterpriseLimitBps", List.of("branch-child"), false, scope()))).getMessage());
  }

  @Test
  void redactsMarginWaterfallForViewerWithoutSensitivePermission() {
    CommandReceipt created = service.createDraft(command("tenant-a", "admin-a", "idem-12", "hash-12", version("v12")));

    var internal = service.simulate("tenant-a", created.policyId(),
        ref -> Optional.ofNullable(Map.of(
            "cfg.companyMarginBps", new BigDecimal("25"),
            "cfg.companyMarginMinBps", new BigDecimal("10"),
            "cfg.companyMarginMaxBps", new BigDecimal("40")).get(ref)),
        new BigDecimal("100.000"));

    var redacted = service.applyVisibility("pricing.margin.view_public", internal);

    assertEquals(internal.policyId(), redacted.policyId());
    assertEquals(internal.versionId(), redacted.versionId());
    assertEquals(internal.priceAfterMargin(), redacted.priceAfterMargin());
    assertEquals("COMPANY_MARGIN", redacted.steps().get(0).stepType());
    assertNull(redacted.steps().get(0).priceBeforeMargin());
    assertNull(redacted.steps().get(0).priceAfterMargin());
    assertNull(redacted.steps().get(0).marginPoints());
    assertEquals(1, service.marginVisibilityRedactionTotal.get());
  }

  @Test
  void allowsSensitiveMarginWaterfallWithPermission() {
    CommandReceipt created = service.createDraft(command("tenant-a", "admin-a", "idem-13", "hash-13", version("v13")));
    var internal = service.simulate("tenant-a", created.policyId(),
        ref -> Optional.ofNullable(Map.of(
            "cfg.companyMarginBps", new BigDecimal("25"),
            "cfg.companyMarginMinBps", new BigDecimal("10"),
            "cfg.companyMarginMaxBps", new BigDecimal("40")).get(ref)),
        new BigDecimal("100.000"));

    var visible = service.applyVisibility(CompanyMarginPolicyService.SENSITIVE_MARGIN_PERMISSION, internal);

    assertEquals(new BigDecimal("100.000"), visible.steps().get(0).priceBeforeMargin());
    assertEquals(new BigDecimal("99.750"), visible.steps().get(0).priceAfterMargin());
    assertEquals(new BigDecimal("0.25"), visible.steps().get(0).marginPoints());
    assertEquals(0, service.marginVisibilityRedactionTotal.get());
  }

  private CommandReceipt publish(CreatePolicyCommand command, String approver) {
    CommandReceipt created = service.createDraft(command);
    service.submit(command.tenantId(), created.policyId(), command.actorId(), "corr-2");
    service.approve(command.tenantId(), created.policyId(), approver, "corr-3");
    return service.publish(command.tenantId(), created.policyId(), approver, "corr-4");
  }

  private static CreatePolicyCommand command(String tenantId, String actorId, String idempotencyKey, String hash,
      MarginPolicyVersion version) {
    return new CreatePolicyCommand(tenantId, "request-1", actorId, idempotencyKey, "corr-1",
        "Conventional Retail Margin " + version.versionId(), version, hash);
  }

  private static MarginPolicyVersion version(String versionId) {
    return new MarginPolicyVersion(versionId, 1, scope(),
        new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
        List.of(new MarginRule(1, MarginUnit.BPS, "cfg.companyMarginBps", "cfg.companyMarginMinBps",
            "cfg.companyMarginMaxBps", 3, "COMPANY_MARGIN")),
        "cfg-hash-1");
  }

  private static MarginPolicyVersion channelVersion(String versionId, List<MarginRule> rules) {
    return new MarginPolicyVersion(versionId, 1, scope(),
        new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null), rules, "cfg-channel-hash-1");
  }

  private static MarginPolicyVersion branchVersion(String versionId, List<MarginRule> rules) {
    return new MarginPolicyVersion(versionId, 1, scope(),
        new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null), rules, "cfg-branch-hash-1");
  }

  private static MarginRule branchRule(int priority, String amountRef, String branchId, String regionId) {
    return new MarginRule(priority, MarginUnit.BPS, amountRef, "cfg.branchMinBps", "cfg.branchMaxBps", 3,
        "BRANCH_OVERLAY", new MarginScope("CONVENTIONAL", "*", "RETAIL", "*", "*", "PURCHASE", "*",
            branchId, regionId, "hierarchy-v1", "INHERIT"));
  }

  private static BranchOverlayContext branchContext(String branchId, List<String> ancestors, boolean hierarchyStale) {
    return new BranchOverlayContext("tenant-a", branchId, "region-east", ancestors, "hierarchy-v1",
        "cfg.enterpriseLimitBps", List.of(branchId), hierarchyStale, scope());
  }

  private static MarginScope scope() {
    return new MarginScope("CONVENTIONAL", "*", "RETAIL", "*", "*", "PURCHASE", "*");
  }
}
