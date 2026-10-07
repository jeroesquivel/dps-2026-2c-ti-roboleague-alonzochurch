package com.dps.roboleague.domain.schedule;

import com.dps.roboleague.domain.shared.ConflictException;
import java.util.List;
import java.util.stream.Collectors;

public class ScheduleConflictException extends ConflictException {

    private final List<ScheduleConflict> conflicts;

    public ScheduleConflictException(List<ScheduleConflict> conflicts) {
        super("the heat cannot be scheduled: " + conflicts.stream()
                .map(conflict -> conflict.type() + " (" + conflict.detail() + ")")
                .collect(Collectors.joining(", ")));
        this.conflicts = List.copyOf(conflicts);
    }

    public List<ScheduleConflict> conflicts() {
        return conflicts;
    }
}
