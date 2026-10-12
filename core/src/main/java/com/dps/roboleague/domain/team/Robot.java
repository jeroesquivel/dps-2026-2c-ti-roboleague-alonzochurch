package com.dps.roboleague.domain.team;

import com.dps.roboleague.domain.competition.RobotClass;
import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.Objects;

public record Robot(String name, RobotClass robotClass, Weight weight, Dimensions dimensions) {

    public Robot {
        Objects.requireNonNull(robotClass, "robot class is required");
        Objects.requireNonNull(dimensions, "robot dimensions are required");
        Objects.requireNonNull(weight, "robot weight is required");
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("robot requires a name");
        }
    }
}
