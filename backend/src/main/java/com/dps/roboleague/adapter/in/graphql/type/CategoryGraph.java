package com.dps.roboleague.adapter.in.graphql.type;

/**
 * Tipo GraphQL {@code Category}. {@code competitionId} no está en el esquema: lo usan los resolvers de
 * {@code teams}, {@code rounds} y {@code standings}.
 */
public record CategoryGraph(String competitionId, String id, String name, AgeRangeGraph ageRange,
        String robotClass) {

    public record AgeRangeGraph(int minimumYears, int maximumYears) {
    }
}
