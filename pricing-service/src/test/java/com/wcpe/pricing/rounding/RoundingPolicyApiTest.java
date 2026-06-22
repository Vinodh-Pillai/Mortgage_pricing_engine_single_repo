package com.wcpe.pricing.rounding;

import com.wcpe.pricing.rounding.api.RoundingPolicyApi;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.CreateRoundingPolicyRequest;
import com.wcpe.pricing.rounding.api.InMemoryRoundingPolicyRepository;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.ResolveRoundingPolicyRequest;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundedValue;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingHeaders;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyConflictException;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyNotSatisfiedException;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyStatus;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyValidationException;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyVersion;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingRule;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingSampleFixture;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundingPolicyApiTest {
    private InMemoryRoundingPolicyRepository repository;
    private RoundingPolicyApi api;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRoundingPolicyRepository();
        api = new RoundingPolicyApi(repository);
    }

    @Test
    void roundingRuleAppliesConfiguredScaleAndIncrement() {
        RoundingPolicyVersion policy = createValidateAndPublish(oneRulePolicy("tenant-a", rule(
                "rule-price", "BASE_PRICE", RoundingUnit.PRICE, 5, RoundingMode.HALF_UP,
                new BigDecimal("0.12500"), 0)));

        RoundedValue rounded = api.resolve("tenant-a", new ResolveRoundingPolicyRequest(
                        "BASE", null, null, "RETAIL", "BASE_PRICE", LocalDate.parse("2026-06-01"),
                        new BigDecimal("100.18749")), readHeaders())
                .roundedValue();

        assertEquals(policy.id(), repository.findById(policy.id()).orElseThrow().id());
        assertEquals(new BigDecimal("100.12500"), rounded.outputValue());
        assertEquals("rule-price", rounded.ruleId());
        assertEquals(RoundingMode.HALF_UP, rounded.roundingMode());
        assertEquals(5, rounded.scale());
        assertEquals(new BigDecimal("0.12500"), rounded.increment());
    }

    @Test
    void roundingPolicyResolverRejectsAmbiguousPolicy() {
        RoundingPolicyVersion first = createValidateAndPublish(oneRulePolicy("tenant-a", rule(
                "rule-money", "CLOSING_COST", RoundingUnit.MONEY, 2, RoundingMode.HALF_UP,
                new BigDecimal("0.01"), 0)));
        RoundingPolicyVersion second = api.createDraft("tenant-a", writeHeaders(), oneRulePolicy("tenant-a", rule(
                "rule-money-two", "CLOSING_COST", RoundingUnit.MONEY, 2, RoundingMode.HALF_UP,
                new BigDecimal("0.01"), 0)));
        repository.save(second.withValidationPassed(true).withPublished("approver-2", first.approvedAt()));

        assertThrows(RoundingPolicyConflictException.class, () -> api.resolve("tenant-a", new ResolveRoundingPolicyRequest(
                "BASE", null, null, "RETAIL", "CLOSING_COST", LocalDate.parse("2026-06-01"),
                new BigDecimal("12.345")), readHeaders()));
    }

    @Test
    void pricingCalculatorFailsWhenRoundingPolicyMissing() {
        assertThrows(RoundingPolicyNotSatisfiedException.class, () -> api.resolve("tenant-a", new ResolveRoundingPolicyRequest(
                "BASE", null, null, "RETAIL", "BASE_PRICE", LocalDate.parse("2026-06-01"),
                new BigDecimal("100.18749")), readHeaders()));
    }

    @Test
    void roundingPolicyPublicationPreventsOverlap() {
        createValidateAndPublish(oneRulePolicy("tenant-a", rule(
                "rule-bps", "ADJUSTMENT_BPS", RoundingUnit.BPS, 2, RoundingMode.HALF_UP,
                new BigDecimal("0.01"), 0)));
        RoundingPolicyVersion overlapping = createAndValidate(oneRulePolicy("tenant-a", rule(
                "rule-bps-next", "ADJUSTMENT_BPS", RoundingUnit.BPS, 2, RoundingMode.HALF_UP,
                new BigDecimal("0.01"), 0)));

        assertThrows(RoundingPolicyConflictException.class,
                () -> api.publish("tenant-a", overlapping.id(), approverHeaders()));
    }

    @Test
    void publishRequiresSeparationOfDutiesAndImmutablePublishedPolicy() {
        RoundingPolicyVersion policy = createAndValidate(oneRulePolicy("tenant-a", rule(
                "rule-note", "NOTE_RATE", RoundingUnit.NOTE_RATE, 5, RoundingMode.HALF_UP,
                new BigDecimal("0.00001"), 0)));

        assertThrows(RoundingPolicyValidationException.class,
                () -> api.publish("tenant-a", policy.id(), creatorPublishHeaders()));

        RoundingPolicyVersion published = api.publish("tenant-a", policy.id(), approverHeaders());
        assertEquals(RoundingPolicyStatus.PUBLISHED, published.status());
        assertNotNull(published.approvedAt());
        assertThrows(RoundingPolicyConflictException.class,
                () -> api.publish("tenant-a", policy.id(), approverHeaders()));
    }

    @Test
    void invalidScaleAndIncrementFailClosed() {
        assertThrows(RoundingPolicyValidationException.class, () -> api.createDraft("tenant-a", writeHeaders(), oneRulePolicy(
                "tenant-a", rule("bad-scale", "BASE_PRICE", RoundingUnit.PRICE, 2, RoundingMode.HALF_UP,
                        new BigDecimal("0.01"), 0))));

        assertThrows(RoundingPolicyValidationException.class, () -> api.createDraft("tenant-a", writeHeaders(), oneRulePolicy(
                "tenant-a", rule("bad-increment", "BASE_PRICE", RoundingUnit.PRICE, 5, RoundingMode.HALF_UP,
                        BigDecimal.ZERO, 0))));
    }

    private RoundingPolicyVersion createValidateAndPublish(CreateRoundingPolicyRequest request) {
        RoundingPolicyVersion policy = createAndValidate(request);
        return api.publish(policy.tenantId(), policy.id(), approverHeaders());
    }

    private RoundingPolicyVersion createAndValidate(CreateRoundingPolicyRequest request) {
        RoundingPolicyVersion policy = api.createDraft(request.tenantId(), writeHeaders(), request);
        assertTrue(api.validatePolicy(policy.tenantId(), policy.id(), writeHeaders()).valid());
        return repository.findById(policy.id()).orElseThrow();
    }

    private static CreateRoundingPolicyRequest oneRulePolicy(String tenantId, RoundingRule rule) {
        return new CreateRoundingPolicyRequest(
                tenantId,
                "BASE",
                null,
                null,
                "RETAIL",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2027-01-01"),
                1,
                List.of(rule),
                List.of(new RoundingSampleFixture("sample-" + rule.ruleId(), rule.outputContext(),
                        new BigDecimal("100.00000"), new BigDecimal("100.00000"))));
    }

    private static RoundingRule rule(
            String ruleId,
            String outputContext,
            RoundingUnit unit,
            int scale,
            RoundingMode roundingMode,
            BigDecimal increment,
            int precedence) {
        return new RoundingRule(ruleId, outputContext, unit, scale, roundingMode, increment, precedence, "SYNTHETIC_POLICY_TEST");
    }

    private static RoundingHeaders writeHeaders() {
        return new RoundingHeaders(Set.of(RoundingPolicyApi.ROUNDING_WRITE_PERMISSION), "creator-1", "corr-001", "idem-001");
    }

    private static RoundingHeaders readHeaders() {
        return new RoundingHeaders(Set.of(RoundingPolicyApi.ROUNDING_READ_PERMISSION), "reader-1", "corr-002", null);
    }

    private static RoundingHeaders approverHeaders() {
        return new RoundingHeaders(Set.of(
                RoundingPolicyApi.ROUNDING_APPROVE_PERMISSION,
                RoundingPolicyApi.ROUNDING_PUBLISH_PERMISSION), "approver-1", "corr-003", "idem-003");
    }

    private static RoundingHeaders creatorPublishHeaders() {
        return new RoundingHeaders(Set.of(
                RoundingPolicyApi.ROUNDING_APPROVE_PERMISSION,
                RoundingPolicyApi.ROUNDING_PUBLISH_PERMISSION), "creator-1", "corr-004", "idem-004");
    }
}
