package com.dps.roboleague.adapter.in.graphql.type;

import java.util.List;

/** Corridas de un equipo agrupadas por ronda. */
public record RoundResultGraph(RoundGraph round, List<RunGraph> runs) {

    public RoundResultGraph {
        runs = List.copyOf(runs);
    }
}
