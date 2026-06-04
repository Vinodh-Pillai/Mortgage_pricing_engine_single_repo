package com.wcpe.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class BestExecutionRanker {
    public List<QuoteOption> rank(List<QuoteCandidate> candidates, RankingPolicy policy, Instant expiresAt) {
        if (policy.rankByCandidateId().isEmpty()) {
            throw new QuoteCreateException("NO_ACTIVE_RANKING_POLICY", "Ranking policy must provide explicit candidate order");
        }

        return candidates.stream()
            .filter(candidate -> policy.rankByCandidateId().containsKey(candidate.candidateId()))
            .map(candidate -> toOption(candidate, policy, expiresAt))
            .sorted(Comparator.comparingInt(QuoteOption::rank))
            .toList();
    }

    private QuoteOption toOption(QuoteCandidate candidate, RankingPolicy policy, Instant expiresAt) {
        BigDecimal finalPrice = candidate.basePriceBps()
            .add(candidate.adjustmentBps())
            .add(candidate.marginBps())
            .setScale(4, RoundingMode.HALF_UP);

        PriceWaterfall waterfall = new PriceWaterfall(
            candidate.basePriceBps(),
            candidate.adjustmentBps(),
            candidate.marginBps(),
            finalPrice,
            candidate.upstreamRefs()
        );

        return new QuoteOption(
            UUID.nameUUIDFromBytes((candidate.candidateId() + ":" + policy.policyVersion()).getBytes()),
            candidate.productId(),
            candidate.investorId(),
            candidate.channel(),
            candidate.lockPeriodDays(),
            candidate.noteRatePercent().setScale(5, RoundingMode.HALF_UP),
            finalPrice,
            candidate.adjustmentBps(),
            candidate.marginBps(),
            waterfall,
            policy.rankByCandidateId().get(candidate.candidateId()),
            policy.scoreByCandidateId().getOrDefault(candidate.candidateId(), BigDecimal.ZERO).setScale(8, RoundingMode.HALF_UP),
            policy.reasonsByCandidateId().getOrDefault(candidate.candidateId(), List.of("configured-ranking-policy")),
            candidate.upstreamRefs(),
            expiresAt
        );
    }
}
