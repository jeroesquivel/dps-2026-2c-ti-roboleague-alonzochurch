package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.shared.RoundId;
import java.util.List;

/** Consulta las corridas de una ronda, ordenadas por número de intento. */
public interface FindRoundRuns {

    List<RunResult> execute(Command command);

    record Command(RoundId roundId) {
    }
}
