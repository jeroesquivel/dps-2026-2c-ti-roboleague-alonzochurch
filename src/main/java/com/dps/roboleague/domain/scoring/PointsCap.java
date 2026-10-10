package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.math.BigDecimal;
import java.util.Objects;

public record PointsCap(Points value) {

    public PointsCap {
        Objects.requireNonNull(value, "points cap value is required");
        if (value.isNegative()) {
            throw new InvalidValueException("a points cap cannot be negative and was " + value);
        }
    }

    public static PointsCap of(String value) {
        return new PointsCap(Points.of(value));
    }

    public static PointsCap of(long value) {
        return new PointsCap(Points.of(value));
    }

    public Points limit(Points earned) {
        return earned.cappedAt(value);
    }

    public Points times(BigDecimal ratio) {
        return value.times(ratio);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
