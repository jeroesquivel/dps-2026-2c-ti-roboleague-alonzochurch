package com.dps.roboleague.domain.team;

import com.dps.roboleague.domain.shared.InvalidValueException;
import java.math.BigDecimal;
import java.util.Objects;

public record Weight(BigDecimal kilograms) {

    public Weight {
        Objects.requireNonNull(kilograms, "kilograms are required");
        if (kilograms.signum() <= 0) {
            throw new InvalidValueException("a weight must be positive");
        }
    }

    public static Weight ofKilograms(String kilograms) {
        return new Weight(new BigDecimal(kilograms));
    }

    public boolean exceeds(Weight limit) {
        return kilograms.compareTo(limit.kilograms) > 0;
    }

    @Override
    public String toString() {
        return kilograms.stripTrailingZeros().toPlainString() + " kg";
    }
}
