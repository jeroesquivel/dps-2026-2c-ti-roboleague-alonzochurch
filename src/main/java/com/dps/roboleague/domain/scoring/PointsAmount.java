package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.math.BigDecimal;
import java.util.Objects;

public record PointsAmount(Points value) {

    public PointsAmount {
        Objects.requireNonNull(value, "points amount value is required");
        if (value.isNegative()) {
            throw new InvalidValueException("a configured points amount cannot be negative and was " + value);
        }
    }

    public static PointsAmount of(String value) {
        return new PointsAmount(Points.of(value));
    }

    public static PointsAmount of(long value) {
        return new PointsAmount(Points.of(value));
    }

    public Points asPoints() {
        return value;
    }

    public Points times(BigDecimal factor) {
        return value.times(factor);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
