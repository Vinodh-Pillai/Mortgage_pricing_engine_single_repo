package com.wcpe.quote;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class TieBreakerEvaluator {
    /**
     * Given candidates that have equal total scores, apply configured tie-breakers
     * in precedence order. Return resolved score deltas and trace.
     * If tie remains after all breakers, use deterministic option ID ordering.
     */
    public TieBreakerResult resolve(List<QuoteCandidate> tiedCandidates, List<TieBreaker> breakers) {
        if (tiedCandidates.size() <= 1) {
            return new TieBreakerResult(List.of(), Map.of(), List.of());
        }

        List<String> trace = List.of();

        // Apply each breaker in precedence order
        List<TieBreaker> sorted = tiedCandidates.size() > 1
            ? breakers.stream()
                .sorted(Comparator.comparingInt(TieBreaker::precedence))
                .toList()
            : List.of();

        for (TieBreaker breaker : sorted) {
            String direction = breaker.direction();
            BigDecimal ascMultiplier = "DESC".equalsIgnoreCase(direction) ? new BigDecimal("-1") : BigDecimal.ONE;

            // Compute breaker score for each candidate
            List<BigDecimal> breakerScores = tiedCandidates.stream()
                .map(c -> getBreakerValue(c, breaker.fieldRef()).multiply(ascMultiplier))
                .toList();

            boolean tieRemains = areAllEqual(breakerScores);
            if (!tieRemains) {
                trace = List.of(breaker.breakerId() + ":" + direction + ":" + breaker.fieldRef());
                Map<String, BigDecimal> deltas = new LinkedHashMap<>();
                IntStream.range(0, tiedCandidates.size())
                    .forEach(i -> deltas.put(tiedCandidates.get(i).candidateId(), breakerScores.get(i)));
                return new TieBreakerResult(deltas.values().stream().toList(), deltas, trace);
            } else {
                trace = List.of(breaker.breakerId() + ":tie_remains");
            }
        }

        // No configured tie-breaker resolved - deterministic option ID ordering
        Map<String, BigDecimal> deltaFallback = new LinkedHashMap<>();
        tiedCandidates.stream()
            .sorted(Comparator.comparing(QuoteCandidate::candidateId))
            .toList()
            .forEach(c -> deltaFallback.put(c.candidateId(), BigDecimal.ZERO));

        return new TieBreakerResult(
            deltaFallback.values().stream().toList(),
            deltaFallback,
            List.of("deterministic-option-id"),
            List.of("UNRESOLVED_TIE")
        );
    }

    private BigDecimal getBreakerValue(QuoteCandidate candidate, String fieldRef) {
        return switch (fieldRef) {
            case "basePriceBps" -> candidate.basePriceBps();
            case "noteRatePercent" -> candidate.noteRatePercent();
            case "marginBps" -> candidate.marginBps();
            case "adjustmentBps" -> candidate.adjustmentBps();
            case "candidateId" -> BigDecimal.valueOf(candidate.candidateId().hashCode());
            default -> BigDecimal.ZERO;
        };
    }

    private boolean areAllEqual(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return true;
        }
        BigDecimal first = values.get(0);
        return values.stream().skip(1).allMatch(v -> v.compareTo(first) == 0);
    }

    public record TieBreakerResult(
        List<BigDecimal> scoreDeltas,
        Map<String, BigDecimal> deltasByCandidateId,
        List<String> trace,
        List<String> warnings
    ) {
        public TieBreakerResult(
            List<BigDecimal> scoreDeltas,
            Map<String, BigDecimal> deltasByCandidateId,
            List<String> trace
        ) {
            this(scoreDeltas, deltasByCandidateId, trace, List.of());
        }

        public TieBreakerResult {
            trace = List.copyOf(trace == null ? List.of() : trace);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }
}
