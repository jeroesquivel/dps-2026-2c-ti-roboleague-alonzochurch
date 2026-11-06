package com.dps.roboleague.results.port.in;

import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import java.util.List;

public interface CaptureRunResult {

    RunId execute(Command command);

    record Command(RoundId roundId, TeamId teamId, AttemptNumber attemptNumber, MeasurementSet measurements,
            JudgeEvaluations evaluations, List<IncidentReport> incidents, Actor actor) {
    }
}
