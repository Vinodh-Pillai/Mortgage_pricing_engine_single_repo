package com.wcpe.adjustment.gridloader;

import com.wcpe.adjustment.CashOutLlpaEvaluator;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class GseGridOverlapValidator {
    public void validate(GseGridMappedRules mappedRules) {
        FicoLtvLlpaEvaluator.validateNoOverlaps(mappedRules.ficoLtvCells());
        CashOutLlpaEvaluator.validateNoOverlaps(mappedRules.cashOutRules());
        validatePropertyOccupancy(mappedRules.propertyOccupancyRules());
    }

    private static void validatePropertyOccupancy(List<com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.PropertyOccupancyRule> rules) {
        for (int i = 0; i < rules.size(); i++) {
            var left = rules.get(i);
            for (int j = i + 1; j < rules.size(); j++) {
                var right = rules.get(j);
                if (sameSelector(left, right) && rangesOverlap(left.ltvMin(), left.ltvMax(), right.ltvMin(), right.ltvMax())
                    && rangesOverlap(left.loanAmountMin(), left.loanAmountMax(), right.loanAmountMin(), right.loanAmountMax())
                    && windowsOverlap(left.effectiveStart(), left.effectiveEnd(), right.effectiveStart(), right.effectiveEnd())) {
                    throw new IllegalArgumentException("overlapping property/occupancy LLPA rules for tenant/rule book/selector");
                }
            }
        }
    }

    private static boolean sameSelector(
        com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.PropertyOccupancyRule left,
        com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.PropertyOccupancyRule right
    ) {
        return left.tenantId().equals(right.tenantId())
            && left.ruleBookId().equals(right.ruleBookId())
            && left.productId().equals(right.productId())
            && left.investorId().equals(right.investorId())
            && left.channel().equals(right.channel())
            && left.occupancyCode().equals(right.occupancyCode())
            && left.propertyTypeCode().equals(right.propertyTypeCode())
            && left.unitMin() <= right.unitMax() && right.unitMin() <= left.unitMax()
            && optionalOverlap(left.projectTypeCode(), right.projectTypeCode())
            && optionalOverlap(left.stateCode(), right.stateCode())
            && optionalOverlap(left.countyCode(), right.countyCode())
            && optionalOverlap(left.manufacturedHousingFlag(), right.manufacturedHousingFlag())
            && optionalOverlap(left.firstTimeHomebuyerFlag(), right.firstTimeHomebuyerFlag());
    }

    private static boolean rangesOverlap(BigDecimal leftMin, BigDecimal leftMax, BigDecimal rightMin, BigDecimal rightMax) {
        if (leftMin == null || leftMax == null || rightMin == null || rightMax == null) return true;
        return leftMin.compareTo(rightMax) <= 0 && rightMin.compareTo(leftMax) <= 0;
    }

    private static boolean windowsOverlap(Instant leftStart, Instant leftEnd, Instant rightStart, Instant rightEnd) {
        Instant normalizedLeftEnd = leftEnd == null ? Instant.MAX : leftEnd;
        Instant normalizedRightEnd = rightEnd == null ? Instant.MAX : rightEnd;
        return leftStart.isBefore(normalizedRightEnd) && rightStart.isBefore(normalizedLeftEnd);
    }

    private static boolean optionalOverlap(Object left, Object right) {
        return left == null || right == null || left.equals(right);
    }
}
