package com.wcpe.margin.overlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OverlayRuleTestFixtures {
  private OverlayRuleTestFixtures() {}

  public static OverlayRuleRepository staticRules(List<OverlayRule> rules) {
    return new StaticOverlayRuleRepository(rules);
  }

  private static final class StaticOverlayRuleRepository implements OverlayRuleRepository {
    private final List<OverlayRule> rules;

    private StaticOverlayRuleRepository(List<OverlayRule> rules) {
      this.rules = List.copyOf(rules == null ? List.of() : rules);
    }

    @Override
    public List<OverlayRule> findApplicable(OverlayInputs inputs) {
      List<OverlayRule> matched = rules.stream()
          .filter(OverlayRule::enabled)
          .filter(rule -> rule.tenantId().equals(inputs.tenantId()))
          .filter(rule -> rule.type().waterfallPosition() == OverlayPolicyType.WaterfallPosition.MARGIN_COMPONENT)
          .filter(rule -> rule.activeAt(inputs.quoteDate()))
          .filter(rule -> rule.scopeMatches(inputs))
          .filter(rule -> inputs.triggerMatches(rule.type()))
          .sorted(Comparator.comparingInt(OverlayRule::priority).reversed())
          .toList();
      return enforceExclusivity(matched);
    }

    private static List<OverlayRule> enforceExclusivity(List<OverlayRule> matched) {
      List<OverlayRule> applied = new ArrayList<>();
      Map<String, OverlayRule> exclusiveWinners = new LinkedHashMap<>();
      for (OverlayRule rule : matched) {
        if (rule.exclusivityGroup() == null) {
          applied.add(rule);
        } else {
          exclusiveWinners.putIfAbsent(rule.exclusivityGroup(), rule);
        }
      }
      applied.addAll(exclusiveWinners.values());
      return applied.stream().sorted(Comparator.comparingInt(OverlayRule::priority).reversed()).toList();
    }
  }
}
