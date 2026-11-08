package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.FindTeamRuns;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import java.util.List;

public final class FindTeamRunsUseCase implements FindTeamRuns {

    private final RunResultRepository runs;

    public FindTeamRunsUseCase(RunResultRepository runs) {
        this.runs = runs;
    }

    @Override
    public List<RunResult> execute(Command command) {
        return runs.findByTeam(command.teamId());
    }
}
