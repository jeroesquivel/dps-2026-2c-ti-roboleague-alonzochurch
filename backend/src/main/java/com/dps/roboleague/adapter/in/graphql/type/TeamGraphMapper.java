package com.dps.roboleague.adapter.in.graphql.type;

import com.dps.roboleague.enrollment.team.TeamRegistration;

/** Traduce una inscripción del núcleo al tipo GraphQL {@code Team}, dejando afuera lo que el esquema no expone. */
public final class TeamGraphMapper {

    private TeamGraphMapper() {
    }

    public static TeamGraph toGraph(TeamRegistration registration) {
        return new TeamGraph(registration.id().value(), registration.name(), registration.status().name(),
                new TeamGraph.RobotGraph(registration.robot().name(), registration.robot().robotClass().code()),
                registration.competitionId().value(), registration.categoryId().value());
    }
}
