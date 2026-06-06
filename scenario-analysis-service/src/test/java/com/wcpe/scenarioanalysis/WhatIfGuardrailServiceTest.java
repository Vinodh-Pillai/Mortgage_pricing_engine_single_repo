package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.WhatIfGuardrailService.CreatePolicyCommand;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.EvaluateCommand;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.GuardrailRule;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.InMemoryWhatIfGuardrailRepository;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.PolicyNotSatisfiedException;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.PublishPolicyCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatIfGuardrailServiceTest {
  private InMemoryWhatIfGuardrailRepository repository;
  private WhatIfGuardrailService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryWhatIfGuardrailRepository();
    service = new WhatIfGuardrailService(
        repository,
        Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void GuardrailEvaluatorTest_blocksCrossTenantSourceQuote() {
    var response = service.evaluate("tenant-001", new EvaluateCommand(
        "CREATE_VARIANT",
        "actor-001",
        Map.of("sourceTenantId", "tenant-002")));

    assertThat(response.severity()).isEqualTo("BLOCK");
    assertThat(response.policyVersion()).isZero();
    assertThat(response.decisions())
        .extracting(WhatIfGuardrailService.GuardrailDecision::ruleCode)
        .contains("CROSS_TENANT_REF", "NO_EFFECTIVE_POLICY");
    assertThat(repository.events())
        .extracting(WhatIfGuardrailService.GuardrailEvent::eventType)
        .contains("whatif.guardrail_violation.blocked.v1");
  }

  @Test
  void GuardrailEvaluatorTest_warnsForNonBindingBorrowerDraft() {
    var policy = service.createPolicy("tenant-001", new CreatePolicyCommand(
        "creator-001",
        List.of(new GuardrailRule("NON_BINDING_BORROWER_DRAFT", "EXPORT", "WARN",
            "Borrower-facing what-if export requires non-binding acknowledgement."),
            new GuardrailRule("PROMOTE_REQUIRES_APPROVAL", "PROMOTE_VARIANT", "BLOCK",
                "Promotion requires configured approval."))));
    service.validatePolicy("tenant-001", policy.policyId());
    service.publishPolicy("tenant-001", policy.policyId(), new PublishPolicyCommand("approver-001"));

    var response = service.evaluate("tenant-001", new EvaluateCommand(
        "EXPORT",
        "actor-001",
        Map.of("recipientType", "INTERNAL", "activeDisclaimerTemplate", "true")));

    assertThat(response.severity()).isEqualTo("WARN");
    assertThat(response.policyVersion()).isEqualTo(1);
    assertThat(response.decisions())
        .extracting(WhatIfGuardrailService.GuardrailDecision::ruleCode)
        .contains("NON_BINDING_BORROWER_DRAFT")
        .doesNotContain("PROMOTE_REQUIRES_APPROVAL");
  }

  @Test
  void GuardrailPolicyLifecycleTest_requiresSeparateApprover() {
    var policy = service.createPolicy("tenant-001", new CreatePolicyCommand(
        "creator-001",
        List.of(new GuardrailRule("BLOCK_SOURCE_QUOTE_MUTATION", "CREATE_VARIANT", "BLOCK",
            "What-if analysis cannot mutate the source quote."))));
    service.validatePolicy("tenant-001", policy.policyId());

    assertThatThrownBy(() -> service.publishPolicy("tenant-001", policy.policyId(), new PublishPolicyCommand("creator-001")))
        .isInstanceOf(PolicyNotSatisfiedException.class)
        .hasMessage("creator cannot publish their own guardrail policy");

    var published = service.publishPolicy("tenant-001", policy.policyId(), new PublishPolicyCommand("approver-001"));

    assertThat(published.status()).isEqualTo("PUBLISHED");
    assertThat(service.effectivePolicy("tenant-001").failClosed()).isFalse();
    assertThat(repository.events())
        .extracting(WhatIfGuardrailService.GuardrailEvent::eventType)
        .contains("whatif.guardrail_policy.published.v1");
  }
}
