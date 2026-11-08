package com.dps.roboleague.adapter.in.graphql.type;

/** Tipo GraphQL {@code Team}: sin integrantes, documentación ni motivos de rechazo. */
public record TeamGraph(String id, String name, String status, RobotGraph robot) {

    public record RobotGraph(String name, String robotClass) {
    }
}
