package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.shared.InvalidValueException;

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
