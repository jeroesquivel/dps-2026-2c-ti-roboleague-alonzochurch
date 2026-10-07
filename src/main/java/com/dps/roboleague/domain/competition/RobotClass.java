package com.dps.roboleague.domain.competition;

import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.Locale;

public record RobotClass(String code) {

    public RobotClass {
        if (code == null || code.isBlank()) {
            throw new InvalidValueException("robot class requires a non blank code");
        }
        code = code.trim().toUpperCase(Locale.ROOT);
    }

    public static RobotClass of(String code) {
        return new RobotClass(code);
    }
}
