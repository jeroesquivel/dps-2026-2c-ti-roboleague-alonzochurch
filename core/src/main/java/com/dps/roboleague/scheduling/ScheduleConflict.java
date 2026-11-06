package com.dps.roboleague.scheduling;

import com.dps.roboleague.shared.InvalidValueException;
import java.util.Objects;

public record ScheduleConflict(ScheduleConflictType type, String detail) {

    public ScheduleConflict {
        Objects.requireNonNull(type, "conflict type is required");
        if (detail == null || detail.isBlank()) {
            throw new InvalidValueException("a schedule conflict requires a detail");
        }
    }
}
