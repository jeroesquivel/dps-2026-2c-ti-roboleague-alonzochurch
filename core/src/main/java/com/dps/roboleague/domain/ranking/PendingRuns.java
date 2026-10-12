package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.shared.ConflictException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record PendingRuns(List<PendingRun> runs) {

    public PendingRuns {
        runs = List.copyOf(runs);
    }

    public static PendingRuns none() {
        return new PendingRuns(List.of());
    }

    public static PendingRuns among(Collection<RunResult> candidates) {
        return new PendingRuns(candidates.stream()
                .filter(run -> !run.completion().isComplete())
                .map(PendingRun::of)
                .toList());
    }

    public void requireNone() {
        if (!runs.isEmpty()) {
            throw new ConflictException(runs.size() + " runs are still pending: " + waitingBySource());
        }
    }

    private String waitingBySource() {
        return Arrays.stream(ResultSource.values())
                .map(source -> Map.entry(source, runs.stream()
                        .filter(run -> run.missingSources().contains(source))
                        .count()))
                .filter(waiting -> waiting.getValue() > 0)
                .map(waiting -> waiting.getValue() + " waiting for " + waiting.getKey())
                .collect(Collectors.joining(", "));
    }
}
