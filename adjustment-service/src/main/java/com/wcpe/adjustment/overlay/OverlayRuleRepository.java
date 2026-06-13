package com.wcpe.adjustment.overlay;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OverlayRuleRepository {
    List<OverlayRule> findApplicable(OverlayInputs inputs);

    default List<OverlayRule> findApplicable(
        UUID tenantId,
        String investorCode,
        String channelCode,
        String productFamily,
        String loanPurpose,
        String occupancy,
        String propertyType,
        String stateCode,
        String countyCode,
        BigDecimal loanAmount,
        boolean escrowWaived,
        String armCaps,
        String buydownType,
        boolean highBalance,
        boolean jumbo,
        Integer loanTermMonths,
        Instant quoteDate
    ) {
        return findApplicable(new OverlayInputs(tenantId, investorCode, channelCode, productFamily, loanPurpose,
            occupancy, propertyType, stateCode, countyCode, loanAmount, escrowWaived, armCaps, buydownType,
            highBalance, jumbo, loanTermMonths, quoteDate));
    }

    static OverlayRuleRepository empty() {
        return new InMemoryOverlayRuleRepository(List.of());
    }

    final class InMemoryOverlayRuleRepository implements OverlayRuleRepository {
        private final List<OverlayRule> rules;

        public InMemoryOverlayRuleRepository(List<OverlayRule> rules) {
            this.rules = List.copyOf(rules == null ? List.of() : rules);
        }

        @Override
        public List<OverlayRule> findApplicable(OverlayInputs inputs) {
            List<OverlayRule> matched = rules.stream()
                .filter(OverlayRule::enabled)
                .filter(rule -> rule.tenantId().equals(inputs.tenantId()))
                .filter(rule -> rule.type().waterfallPosition() == OverlayPolicyType.WaterfallPosition.LLPA_ADJUSTMENT)
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
            return applied.stream()
                .sorted(Comparator.comparingInt(OverlayRule::priority).reversed())
                .toList();
        }
    }
}
