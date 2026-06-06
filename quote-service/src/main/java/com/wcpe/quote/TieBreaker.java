package com.wcpe.quote;

import java.util.Map;

public record TieBreaker(
    String breakerId,
    String fieldRef,
    String direction,
    int precedence,
    Map<String, Object> config
) {
    public TieBreaker {
        direction = direction == null ? "ASC" : direction;
        config = Map.copyOf(config == null ? Map.of() : config);
    }
}
