package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.shared.InvalidValueException;

public record AttemptNumber(int value) implements Comparable<AttemptNumber> {

    public AttemptNumber {
        if (value < 1) {
            throw new InvalidValueException("an attempt number must be positive");
        }
    }

    public static AttemptNumber of(int value) {
        return new AttemptNumber(value);
    }

    public static AttemptNumber first() {
        return new AttemptNumber(1);
    }

    @Override
    public int compareTo(AttemptNumber other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
