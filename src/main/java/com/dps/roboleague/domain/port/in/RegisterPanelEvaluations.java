package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.List;

public interface RegisterPanelEvaluations {

    RunId execute(Command command);

    record Command(RoundId roundId, TeamId teamId, AttemptNumber attemptNumber, JudgeEvaluations evaluations,
            List<IncidentReport> incidents, Actor actor) {
    }
}
