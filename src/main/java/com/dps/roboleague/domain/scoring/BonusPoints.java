package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.util.Objects;

public record BonusPoints(Points value) {

    public BonusPoints {
        Objects.requireNonNull(value, "bonus points value is required");
        if (value.isNegative()) {
            throw new InvalidValueException("bonus points cannot be negative and were " + value);
        }
    }

    public static BonusPoints of(long value) {
        return new BonusPoints(Points.of(value));
    }

    public Points asPoints() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
