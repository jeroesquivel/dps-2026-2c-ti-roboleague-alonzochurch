package com.dps.roboleague.rules.scoring;

import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.Points;
import java.math.BigDecimal;
import java.util.Objects;

public record PointsRate(BigDecimal value) {

    public PointsRate {
        Objects.requireNonNull(value, "points rate value is required");
        if (value.signum() < 0) {
            throw new InvalidValueException("a points rate cannot be negative and was " + value.toPlainString());
        }
    }

    public static PointsRate of(String value) {
        return new PointsRate(new BigDecimal(value));
    }

    public static PointsRate of(long value) {
        return new PointsRate(BigDecimal.valueOf(value));
    }

    public Points times(BigDecimal units) {
        return Points.of(value.multiply(units));
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
