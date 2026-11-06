package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.FindRunResult;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RunId;

public final class FindRunResultUseCase implements FindRunResult {

    private final RunResultRepository runResults;

    public FindRunResultUseCase(RunResultRepository runResults) {
        this.runResults = runResults;
    }

    @Override
    public RunResult execute(RunId runId) {
        return runResults.findById(runId)
                .orElseThrow(() -> NotFoundException.of("RunResult", runId.value()));
    }
}
