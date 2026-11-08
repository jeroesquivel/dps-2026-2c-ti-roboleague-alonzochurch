package com.dps.roboleague.enrollment.port.in;

import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.shared.TeamId;
import java.util.Map;
import java.util.Set;

/**
 * Varias inscripciones en una sola consulta, para quien necesita muchos equipos a la vez sin pedirlos de a uno (la
 * carga por lotes de la API GraphQL). Si alguno no existe, la consulta falla igual que {@link FindTeamRegistration}.
 */
public interface FindTeamRegistrations {

    Map<TeamId, TeamRegistration> execute(Command command);

    record Command(Set<TeamId> teamIds) {

        public Command {
            teamIds = Set.copyOf(teamIds);
        }
    }
}
