package com.dps.roboleague.enrollment.competition;

import com.dps.roboleague.shared.AgeRange;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.InvalidValueException;
import java.util.Objects;

public record Category(CategoryId id, String name, AgeRange ageRange, RobotClass robotClass) {

    public Category {
        Objects.requireNonNull(id, "category id is required");
        Objects.requireNonNull(ageRange, "age range is required");
        Objects.requireNonNull(robotClass, "robot class is required");
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("category requires a name");
        }
    }
}
