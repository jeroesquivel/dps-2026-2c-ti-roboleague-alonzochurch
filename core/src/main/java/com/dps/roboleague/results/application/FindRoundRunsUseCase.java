package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.FindRoundRuns;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import java.util.List;

public final class FindRoundRunsUseCase implements FindRoundRuns {

    private final RunResultRepository runs;

    public FindRoundRunsUseCase(RunResultRepository runs) {
        this.runs = runs;
    }

    @Override
    public List<RunResult> execute(Command command) {
        return runs.findByRound(command.roundId());
    }
}
