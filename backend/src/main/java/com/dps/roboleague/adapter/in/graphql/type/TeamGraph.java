package com.dps.roboleague.adapter.in.graphql.type;

/** Tipo GraphQL {@code Team}: sin integrantes, documentación ni motivos de rechazo. */
public record TeamGraph(String id, String name, String status, RobotGraph robot, String competitionId,
        String categoryId) {

    public record RobotGraph(String name, String robotClass) {
    }
}
