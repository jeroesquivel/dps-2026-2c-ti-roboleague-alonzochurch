package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.shared.RunId;

public interface FindRunResult {

    RunResult execute(RunId runId);
}
