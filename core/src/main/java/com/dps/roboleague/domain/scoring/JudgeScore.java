package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record JudgeScore(BigDecimal value) {

    public static final BigDecimal MINIMUM = BigDecimal.ZERO;
    public static final BigDecimal MAXIMUM = BigDecimal.TEN;

    public JudgeScore {
        Objects.requireNonNull(value, "judge score value is required");
        if (value.compareTo(MINIMUM) < 0 || value.compareTo(MAXIMUM) > 0) {
            throw new InvalidValueException("a judge score must be between %s and %s and was %s"
                    .formatted(MINIMUM, MAXIMUM, value.toPlainString()));
        }
        value = value.setScale(Points.SCALE, RoundingMode.HALF_UP);
    }

    public static JudgeScore of(long value) {
        return new JudgeScore(BigDecimal.valueOf(value));
    }
}
