package com.dps.roboleague.rules.challenge;

import com.dps.roboleague.shared.InvalidValueException;

public record MetricUnit(String symbol) {

    public MetricUnit {
        if (symbol == null || symbol.isBlank()) {
            throw new InvalidValueException("a metric unit requires a non blank symbol");
        }
        symbol = symbol.trim();
    }

    public static MetricUnit of(String symbol) {
        return new MetricUnit(symbol);
    }
}
