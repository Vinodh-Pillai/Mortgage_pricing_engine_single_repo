package com.wcpe.adjustment.gridloader;

import com.wcpe.adjustment.CashOutLlpaEvaluator.CashOutLlpaRule;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.GridCell;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.PropertyOccupancyRule;
import java.util.List;

public record GseGridMappedRules(
    String investorCode,
    String ruleBookVersion,
    String ruleBookHash,
    List<GridCell> ficoLtvCells,
    List<CashOutLlpaRule> cashOutRules,
    List<PropertyOccupancyRule> propertyOccupancyRules
) {
    public GseGridMappedRules {
        ficoLtvCells = List.copyOf(ficoLtvCells == null ? List.of() : ficoLtvCells);
        cashOutRules = List.copyOf(cashOutRules == null ? List.of() : cashOutRules);
        propertyOccupancyRules = List.copyOf(propertyOccupancyRules == null ? List.of() : propertyOccupancyRules);
    }

    int cellCount() {
        return ficoLtvCells.size() + cashOutRules.size() + propertyOccupancyRules.size();
    }
}
