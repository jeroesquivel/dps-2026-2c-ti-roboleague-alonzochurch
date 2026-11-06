package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.result.ResultAmendment;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.RunId;

/** Corrige un resultado por un error de carga, sin que haya mediado una apelación. */
public interface CorrectResult {

    RunResult execute(Command command);

    record Command(RunId runId, String reason, ResultAmendment amendment, Actor actor) {
    }
}
