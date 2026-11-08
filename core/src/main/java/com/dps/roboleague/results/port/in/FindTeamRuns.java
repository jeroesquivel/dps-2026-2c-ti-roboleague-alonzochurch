package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.shared.TeamId;
import java.util.List;

/** Consulta todas las corridas de un equipo. */
public interface FindTeamRuns {

    List<RunResult> execute(Command command);

    record Command(TeamId teamId) {
    }
}
