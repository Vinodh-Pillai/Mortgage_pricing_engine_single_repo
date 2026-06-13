package com.wcpe.quote;

import java.util.List;
import java.util.Map;

public record BestExecutionRankingResult(List<QuoteOption> rankedOptions, Map<String, EligibilityDecision> ineligibleCandidates) {}
