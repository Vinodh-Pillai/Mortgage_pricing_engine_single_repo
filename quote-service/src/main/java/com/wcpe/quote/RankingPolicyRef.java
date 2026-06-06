package com.wcpe.quote;

import java.util.List;
import java.util.Map;

public record RankingPolicyRef(
    String policyId,
    String policyVersion,
    List<RankingCriterion> criteria,
    List<TieBreaker> tieBreakers,
    Map<String, String> metadata
) {
    public RankingPolicyRef {
        criteria = List.copyOf(criteria == null ? List.of() : criteria);
        tieBreakers = List.copyOf(tieBreakers == null ? List.of() : tieBreakers);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
