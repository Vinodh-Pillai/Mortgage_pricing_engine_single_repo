package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.margin.CompanyMarginPolicyService.CommandReceipt;
import com.wcpe.margin.CompanyMarginPolicyService.CreatePolicyCommand;
import com.wcpe.margin.CompanyMarginPolicyService.EffectiveWindow;
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

  private CommandReceipt publish(CreatePolicyCommand command, String approver) {
    CommandReceipt created = service.createDraft(command);
    service.submit(command.tenantId(), created.policyId(), command.actorId(), "corr-2");
    service.approve(command.tenantId(), created.policyId(), approver, "corr-3");
    return service.publish(command.tenantId(), created.policyId(), approver, "corr-4");
  }

  private static CreatePolicyCommand command(String tenantId, String actorId, String idempotencyKey, String hash,
      MarginPolicyVersion version) {
    return new CreatePolicyCommand(tenantId, "request-1", actorId, idempotencyKey, "corr-1",
        "Conventional Retail Company Margin", version, hash);
  }

  private static MarginPolicyVersion version(String versionId) {
    return new MarginPolicyVersion(versionId, 1, scope(),
        new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
        List.of(new MarginRule(1, MarginUnit.BPS, "cfg.companyMarginBps", "cfg.companyMarginMinBps",
            "cfg.companyMarginMaxBps", 3, "COMPANY_MARGIN")),
        "cfg-hash-1");
  }

  private static MarginScope scope() {
    return new MarginScope("CONVENTIONAL", "*", "RETAIL", "*", "*", "PURCHASE", "*");
  }
}
