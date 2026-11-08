package com.dps.roboleague.adapter.in.graphql.type;

/** Tipo GraphQL {@code Run}; los ids relacionados alimentan resolvers y no forman parte del esquema. */
public record RunGraph(String id, int attemptNumber, String status, String teamId, String roundId) {
}
