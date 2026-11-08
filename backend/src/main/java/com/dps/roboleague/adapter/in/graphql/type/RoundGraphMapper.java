package com.dps.roboleague.adapter.in.graphql.type;

import com.dps.roboleague.scheduling.Heat;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.shared.JudgeId;
import java.time.format.DateTimeFormatter;

/** Traduce una ronda del núcleo al tipo GraphQL {@code Round}, con los mismos formatos que la API REST. */
public final class RoundGraphMapper {

    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private RoundGraphMapper() {
    }

    public static RoundGraph toGraph(Round round) {
        return new RoundGraph(round.id().value(), round.ordinal().value(), round.challengeId().value(),
                round.rulebookVersion().toString(),
                round.heats().entries().stream().map(RoundGraphMapper::toGraph).toList());
    }

    private static RoundGraph.HeatGraph toGraph(Heat heat) {
        return new RoundGraph.HeatGraph(heat.teamId().value(), heat.arenaId().value(),
                LOCAL_DATE_TIME.format(heat.slot().start()), LOCAL_DATE_TIME.format(heat.slot().end()),
                heat.judges().stream().map(JudgeId::value).sorted().toList());
    }
}
