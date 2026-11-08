package com.dps.roboleague.adapter.in.graphql.type;

import com.dps.roboleague.results.result.RunResult;

public final class RunGraphMapper {

    private RunGraphMapper() {
    }

    public static RunGraph toGraph(RunResult run) {
        return new RunGraph(run.id().value(), run.attemptNumber().value(), run.status().name(),
                run.teamId().value(), run.roundId().value());
    }
}
