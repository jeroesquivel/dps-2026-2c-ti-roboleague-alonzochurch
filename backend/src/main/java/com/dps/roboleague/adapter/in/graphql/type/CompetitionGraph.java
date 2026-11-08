package com.dps.roboleague.adapter.in.graphql.type;

import java.util.List;

/** Tipo GraphQL {@code Competition}. */
public record CompetitionGraph(String id, String name, PeriodGraph period, String activeRulebookVersion,
        List<CategoryGraph> categories) {

    public record PeriodGraph(String start, String end) {
    }
}
