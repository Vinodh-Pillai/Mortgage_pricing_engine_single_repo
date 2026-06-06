package com.wcpe.quote;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record RankingPolicy(
    String policyId,
    String policyVersion,
    Duration quoteTtl,
    Map<String, Integer> rankByCandidateId,
    Map<String, BigDecimal> scoreByCandidateId,
    Map<String, List<String>> reasonsByCandidateId,
    RankingPolicyRef policyRef
) {
    public RankingPolicy {
        rankByCandidateId = Map.copyOf(rankByCandidateId == null ? Map.of() : rankByCandidateId);
        scoreByCandidateId = Map.copyOf(scoreByCandidateId == null ? Map.of() : scoreByCandidateId);
        reasonsByCandidateId = Map.copyOf(reasonsByCandidateId == null ? Map.of() : reasonsByCandidateId);
    }
}
