package com.wcpe.quote;

import java.util.List;

public record QuoteFilters(
    List<String> productTypeIds,
    List<String> investorIds,
    List<String> channels,
    List<String> states
) {
    public QuoteFilters {
        productTypeIds = List.copyOf(productTypeIds == null ? List.of() : productTypeIds);
        investorIds = List.copyOf(investorIds == null ? List.of() : investorIds);
        channels = List.copyOf(channels == null ? List.of() : channels);
        states = List.copyOf(states == null ? List.of() : states);
    }
}
