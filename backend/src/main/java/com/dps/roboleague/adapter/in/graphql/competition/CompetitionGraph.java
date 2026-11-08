package com.dps.roboleague.adapter.in.graphql.competition;

import java.util.List;

/** Tipo GraphQL {@code Competition}: los nombres de los componentes son los campos del esquema. */
public record CompetitionGraph(String id, String name, PeriodGraph period, String activeRulebookVersion,
        List<CategoryGraph> categories) {

    public record PeriodGraph(String start, String end) {
    }

    public record CategoryGraph(String id, String name, AgeRangeGraph ageRange, String robotClass) {
    }

    public record AgeRangeGraph(int minimumYears, int maximumYears) {
    }
}
