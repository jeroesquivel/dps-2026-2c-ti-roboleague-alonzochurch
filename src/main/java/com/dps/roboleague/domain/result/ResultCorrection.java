package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ResultCorrection(Instant appliedAt, Actor actor, String reason, MeasurementSet measurements,
        List<IncidentReport> incidents, AppealId sourceAppeal) {

    public ResultCorrection {
        Objects.requireNonNull(appliedAt, "correction timestamp is required");
        Objects.requireNonNull(actor, "correction actor is required");
        Objects.requireNonNull(measurements, "corrected measurements are required");
        Objects.requireNonNull(sourceAppeal, "source appeal is required");
        if (reason == null || reason.isBlank()) {
            throw new InvalidValueException("a correction requires a reason");
        }
        incidents = List.copyOf(incidents);
    }
}
