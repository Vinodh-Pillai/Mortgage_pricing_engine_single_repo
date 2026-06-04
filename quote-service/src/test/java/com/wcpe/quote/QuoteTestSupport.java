package com.wcpe.quote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuoteTestSupport {
    public static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID SCENARIO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-04T12:00:00Z"), ZoneOffset.UTC);

    private QuoteTestSupport() {
    }

    public static QuoteCreateRequest request(String idempotencyKey) {
        return new QuoteCreateRequest(
            TENANT,
            SCENARIO,
            7,
            List.of(30, 45),
            new QuoteFilters(List.of("fixture-product"), List.of("fixture-investor"), List.of("retail"), List.of("CA")),
            "USD",
            Map.of("source", "unit-test"),
            "actor-1",
            idempotencyKey,
            "corr-1",
            LocalDate.parse("2026-06-04")
        );
    }

    public static QuoteApplicationService service(QuoteDependencies dependencies, InMemoryQuoteCache cache) {
        return new QuoteApplicationService(new InMemoryQuoteRepository(), dependencies, cache, new BestExecutionRanker(), CLOCK);
    }

    public static FixtureDependencies dependenciesWithPolicy() {
        return new FixtureDependencies(true, List.of(candidate("A"), candidate("B")));
    }

    public static QuoteCandidate candidate(String id) {
        return new QuoteCandidate(
            id,
            "eligibility-" + id,
            "product-" + id,
            "investor-" + id,
            "retail",
            30,
            new BigDecimal("6.12500"),
            new BigDecimal("10000.0000"),
            new BigDecimal("25.2500"),
            new BigDecimal("10.0000"),
            Map.of("eligibilityRef", "eligibility-" + id, "priceRef", "price-" + id)
        );
    }

    public record FixtureDependencies(boolean hasPolicy, List<QuoteCandidate> candidates) implements QuoteDependencies {
        @Override
        public Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request) {
            if (!hasPolicy) {
                return Optional.empty();
            }
            return Optional.of(new RankingPolicy(
                "ranking-policy-fixture",
                "rank-v1",
                Duration.ofHours(4),
                Map.of("A", 2, "B", 1),
                Map.of("A", new BigDecimal("0.50000000"), "B", new BigDecimal("0.90000000")),
                Map.of("A", List.of("fixture configured rank 2"), "B", List.of("fixture configured rank 1"))
            ));
        }

        @Override
        public List<QuoteCandidate> candidatesFor(QuoteCreateRequest request) {
            return candidates;
        }

        @Override
        public String eligibilityVersion() {
            return "eligibility-v1";
        }

        @Override
        public String pricingVersion() {
            return "pricing-v1";
        }

        @Override
        public String adjustmentVersion() {
            return "adjustment-v1";
        }

        @Override
        public String marginVersion() {
            return "margin-v1";
        }
    }
}
