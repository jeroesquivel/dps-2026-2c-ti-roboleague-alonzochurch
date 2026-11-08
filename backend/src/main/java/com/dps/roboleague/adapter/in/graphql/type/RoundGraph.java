package com.dps.roboleague.adapter.in.graphql.type;

import java.util.List;

/** Tipo GraphQL {@code Round}. */
public record RoundGraph(String id, int ordinal, String challengeId, String rulebookVersion, List<HeatGraph> heats) {

    /** Tipo GraphQL {@code Heat}. {@code teamId} no está en el esquema: lo usa el resolver de {@code team}. */
    public record HeatGraph(String teamId, String arena, String start, String end, List<String> judges) {
    }
}
