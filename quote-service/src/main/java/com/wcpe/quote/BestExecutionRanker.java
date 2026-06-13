package com.wcpe.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BestExecutionRanker {
    private final CriterionEvaluator criterionEvaluator = new CriterionEvaluator();
    private final TieBreakerEvaluator tieBreakerEvaluator = new TieBreakerEvaluator();
    private final EligibilityFilter eligibilityFilter;

    public BestExecutionRanker() {
        this(EligibilityFilter.allowAll());
    }

    public BestExecutionRanker(EligibilityFilter eligibilityFilter) {
        this.eligibilityFilter = eligibilityFilter == null ? EligibilityFilter.allowAll() : eligibilityFilter;
    }

    public List<QuoteOption> rank(List<QuoteCandidate> candidates, RankingPolicy policy, Instant expiresAt) {
        return rankWithEligibility(candidates, policy, expiresAt).rankedOptions();
    }

    public BestExecutionRankingResult rankWithEligibility(List<QuoteCandidate> candidates, RankingPolicy policy, Instant expiresAt) {
        RankingPolicyRef ref = policy.policyRef();
        if (ref == null || ref.criteria().isEmpty()) {
            throw new QuoteCreateException("NO_ACTIVE_RANKING_POLICY", "Ranking policy must provide explicit criteria");
        }

        Map<String, EligibilityDecision> ineligible = new LinkedHashMap<>();
        List<QuoteCandidate> eligibleCandidates = candidates.stream()
            .filter(candidate -> {
                EligibilityDecision decision = eligibilityFilter.evaluate(candidate);
                if (!decision.eligible()) {
                    ineligible.put(candidate.candidateId(), decision);
                    return false;
                }
                return true;
            })
            .toList();

        List<ScoredCandidate> scored = eligibleCandidates.stream()
            .map(candidate -> score(candidate, ref))
            .collect(Collectors.toList());

        // Group by score for tie-breaking
        Map<String, List<ScoredCandidate>> scoreGroups = groupByScore(scored, policy);

        List<ScoredCandidate> resolved = new java.util.ArrayList<>();
        for (Map.Entry<String, List<ScoredCandidate>> group : scoreGroups.entrySet()) {
            if (group.getValue().size() == 1) {
                resolved.add(group.getValue().get(0));
            } else {
                List<QuoteCandidate> tiedCandidates = group.getValue().stream().map(ScoredCandidate::candidate).toList();
                TieBreakerEvaluator.TieBreakerResult tieResult = tieBreakerEvaluator.resolve(tiedCandidates, ref.tieBreakers());
                List<ScoredCandidate> resolvedGroup = group.getValue().stream()
                    .map(sc -> applyTieDelta(sc, tieResult))
                    .toList();
                resolved.addAll(resolvedGroup);
            }
        }

        // Sort by aggregated score descending, then neutral candidate ID ordering for unresolved ties.
        resolved.sort(Comparator.comparing((ScoredCandidate sc) -> sc.totalScore()).reversed()
            .thenComparing(sc -> sc.candidate().candidateId()));

        // Assign ranks and build options
        List<QuoteOption> options = IntStream.range(0, resolved.size())
            .mapToObj(i -> toOption(resolved.get(i), i + 1, policy, expiresAt))
            .toList();
        return new BestExecutionRankingResult(options, Map.copyOf(ineligible));
    }

    private ScoredCandidate score(QuoteCandidate candidate, RankingPolicyRef policyRef) {
        List<BigDecimal> criterionScores = new java.util.ArrayList<>();
        List<String> reasons = new java.util.ArrayList<>();
        reasons.add("policy:" + policyRef.policyId() + ":" + policyRef.policyVersion());
        BigDecimal totalScore = BigDecimal.ZERO;

        for (RankingCriterion criterion : policyRef.criteria()) {
            try {
                CriterionEvaluator.EvaluationResult result = criterionEvaluator.evaluate(criterion, candidate);
                BigDecimal weighted = result.normalizedScore().multiply(criterion.weight()).setScale(8, RoundingMode.HALF_UP);
                criterionScores.add(weighted);
                reasons.add(formatReason(result.reason()));
                totalScore = totalScore.add(weighted);
            } catch (QuoteCreateException ex) {
                if (criterion.required()) {
                    throw ex;
                }
                criterionScores.add(BigDecimal.ZERO);
                reasons.add("criterion_non_applicable:" + criterion.criterionId());
            }
        }

        return new ScoredCandidate(candidate, totalScore.setScale(8, RoundingMode.HALF_UP), criterionScores, reasons, List.of(), List.of());
    }

    private ScoredCandidate applyTieDelta(ScoredCandidate sc, TieBreakerEvaluator.TieBreakerResult tieResult) {
        BigDecimal delta = tieResult.deltasByCandidateId().getOrDefault(sc.candidate().candidateId(), BigDecimal.ZERO);
        List<String> trace = new java.util.ArrayList<>(sc.tieBreakerTrace());
        List<String> warnings = new java.util.ArrayList<>(sc.warnings());
        trace.addAll(tieResult.trace());
        warnings.addAll(tieResult.warnings());
        BigDecimal newScore = sc.totalScore().add(delta).setScale(8, RoundingMode.HALF_UP);
        return new ScoredCandidate(sc.candidate(), newScore, sc.criterionScores(), sc.reasons(), trace, warnings);
    }

    private Map<String, List<ScoredCandidate>> groupByScore(List<ScoredCandidate> scored, RankingPolicy policy) {
        Map<String, List<ScoredCandidate>> groups = new LinkedHashMap<>();
        for (ScoredCandidate sc : scored) {
            String groupKey = sc.totalScore().toPlainString();
            groups.computeIfAbsent(groupKey, k -> new java.util.ArrayList<>()).add(sc);
        }
        return groups;
    }

    private String formatReason(RankReason reason) {
        return reason.criterionId()
            + ":" + reason.reasonType()
            + ":" + reason.description()
            + ":" + reason.evidence();
    }

    private QuoteOption toOption(ScoredCandidate sc, int rank, RankingPolicy policy, Instant expiresAt) {
        BigDecimal finalPrice = sc.candidate().basePriceBps()
            .add(sc.candidate().adjustmentBps())
            .add(sc.candidate().marginBps())
            .setScale(4, RoundingMode.HALF_UP);

        PriceWaterfall waterfall = new PriceWaterfall(
            sc.candidate().basePriceBps(),
            sc.candidate().adjustmentBps(),
            sc.candidate().marginBps(),
            finalPrice,
            sc.candidate().upstreamRefs(),
            sc.candidate().adjustmentResult() == null ? List.of() : sc.candidate().adjustmentResult().adjustments()
        );

        return new QuoteOption(
            UUID.nameUUIDFromBytes((sc.candidate().candidateId() + ":" + policy.policyVersion()).getBytes()),
            sc.candidate().productId(),
            sc.candidate().investorId(),
            sc.candidate().channel(),
            sc.candidate().lockPeriodDays(),
            sc.candidate().noteRatePercent().setScale(5, RoundingMode.HALF_UP),
            finalPrice,
            sc.candidate().adjustmentBps(),
            sc.candidate().marginBps(),
            waterfall,
            rank,
            sc.totalScore(),
            sc.reasons(),
            sc.criterionScores(),
            String.join(";", sc.tieBreakerTrace()),
            sc.warnings(),
            sc.candidate().upstreamRefs(),
            expiresAt
        );
    }

    record ScoredCandidate(
        QuoteCandidate candidate,
        BigDecimal totalScore,
        List<BigDecimal> criterionScores,
        List<String> reasons,
        List<String> tieBreakerTrace,
        List<String> warnings
    ) {}
}
