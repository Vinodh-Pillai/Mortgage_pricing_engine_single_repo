package com.wcpe.observability.loadtest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PricingLoadScenarioMix {
  private final Map<PricingLoadScenarioType, Integer> percentageByType;

  public PricingLoadScenarioMix(Map<PricingLoadScenarioType, Integer> percentageByType) {
    if (percentageByType == null || percentageByType.isEmpty()) {
      throw new IllegalArgumentException("pricing load scenario mix is required");
    }
    this.percentageByType = Map.copyOf(percentageByType);
    if (this.percentageByType.values().stream().mapToInt(Integer::intValue).sum() != 100) {
      throw new IllegalArgumentException("pricing load scenario mix must total 100 percent");
    }
    if (this.percentageByType.values().stream().anyMatch(value -> value <= 0)) {
      throw new IllegalArgumentException("pricing load scenario mix percentages must be positive");
    }
  }

  public static PricingLoadScenarioMix fromStoryDefaults() {
    return new PricingLoadScenarioMix(Arrays.stream(PricingLoadScenarioType.values())
        .collect(Collectors.toMap(type -> type, PricingLoadScenarioType::storyPercentage)));
  }

  public int percentageFor(PricingLoadScenarioType type) {
    Integer percentage = percentageByType.get(type);
    if (percentage == null) {
      throw new IllegalStateException("POLICY_NOT_SATISFIED: scenario mix missing " + type.code());
    }
    return percentage;
  }

  public List<String> weightedScenarioCodes() {
    return Arrays.stream(PricingLoadScenarioType.values())
        .map(type -> type.code() + ":" + percentageFor(type))
        .toList();
  }

  public Map<PricingLoadScenarioType, Integer> percentageByType() {
    return percentageByType;
  }
}
