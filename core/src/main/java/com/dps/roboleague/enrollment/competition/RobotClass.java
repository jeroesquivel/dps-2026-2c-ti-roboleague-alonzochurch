package com.dps.roboleague.enrollment.competition;

import com.dps.roboleague.shared.InvalidValueException;
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
