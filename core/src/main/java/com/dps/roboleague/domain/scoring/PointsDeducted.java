package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.math.BigDecimal;
import java.util.Objects;

public record PointsDeducted(Points value) {

    public PointsDeducted {
        Objects.requireNonNull(value, "points deducted value is required");
        if (value.isNegative()) {
            throw new InvalidValueException("points deducted cannot be negative and were " + value);
        }
    }

    public static PointsDeducted of(long value) {
        return new PointsDeducted(Points.of(value));
    }

    public Points times(BigDecimal factor) {
        return value.times(factor);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
