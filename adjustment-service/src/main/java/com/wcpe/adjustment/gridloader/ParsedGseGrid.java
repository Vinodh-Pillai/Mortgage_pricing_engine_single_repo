package com.wcpe.adjustment.gridloader;

import java.util.List;

public record ParsedGseGrid(String investorCode, List<GseGridRow> rows, List<GridParseWarning> warnings) {
    public ParsedGseGrid {
        rows = List.copyOf(rows == null ? List.of() : rows);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
