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
import java.util.Set;
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
        return new QuoteApplicationService(
            new InMemoryQuoteRepository(),
            new InMemoryQuoteJobRepository(),
            new InMemoryQuoteSnapshotRepository(),
            dependencies,
            cache,
            new BestExecutionRanker(),
            CLOCK
        );
    }

    public static ComparisonViewConfig comparisonView() {
        return new ComparisonViewConfig(
            "default-view",
            "view-v1",
            List.of(
                new ComparisonColumn("rank", "Rank", 1),
                new ComparisonColumn("productLabel", "Product", 2),
                new ComparisonColumn("investorLabel", "Investor", 3),
                new ComparisonColumn("noteRate", "Note Rate", 4),
                new ComparisonColumn("pricePoints", "Price/Points", 5),
                new ComparisonColumn("lockDays", "Lock Days", 6),
                new ComparisonColumn("totalAdjustments", "Total Adjustments", 7),
                new ComparisonColumn("margin", "Margin", 8),
                new ComparisonColumn("expiration", "Expiration", 9)
            ),
            Set.of("margin", "investorLabel"),
            null,
            3,
            "audit-safe-redaction-v1"
        );
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
                Map.of("A", List.of("fixture configured rank 2"), "B", List.of("fixture configured rank 1")),
                new RankingPolicyRef(
                    "ranking-policy-fixture",
                    "rank-v1",
                    List.of(new RankingCriterion("fixture-price", "numeric_min", "basePriceBps", BigDecimal.ONE, true, "{}")),
                    List.of(new TieBreaker("fixture-candidate-id", "candidateId", "DESC", 1, Map.of())),
                    Map.of("source", "unit-test")
                )
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
