package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;

public interface RegisterAutomaticMeasurements {

    RunId execute(Command command);

    record Command(RoundId roundId, TeamId teamId, AttemptNumber attemptNumber, MeasurementSet measurements,
            Actor actor) {
    }
}
