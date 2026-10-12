package com.dps.roboleague.domain.schedule;

import com.dps.roboleague.domain.shared.InvalidValueException;

public record RoundOrdinal(int value) implements Comparable<RoundOrdinal> {

    public RoundOrdinal {
        if (value < 1) {
            throw new InvalidValueException("a round ordinal must be positive");
        }
    }

    public static RoundOrdinal of(int value) {
        return new RoundOrdinal(value);
    }

    @Override
    public int compareTo(RoundOrdinal other) {
        return Integer.compare(value, other.value);
    }
}
