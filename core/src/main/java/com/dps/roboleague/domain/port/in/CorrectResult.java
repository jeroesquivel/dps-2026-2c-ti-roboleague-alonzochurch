package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.result.ResultAmendment;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.RunId;

/** Corrige un resultado por un error de carga, sin que haya mediado una apelación. */
public interface CorrectResult {

    RunResult execute(Command command);

    record Command(RunId runId, String reason, ResultAmendment amendment, Actor actor) {
    }
}
