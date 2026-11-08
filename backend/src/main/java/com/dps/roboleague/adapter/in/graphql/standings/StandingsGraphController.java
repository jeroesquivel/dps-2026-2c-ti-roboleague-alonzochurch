package com.dps.roboleague.adapter.in.graphql.standings;

import com.dps.roboleague.adapter.in.graphql.type.StandingsGraph;
import com.dps.roboleague.adapter.in.graphql.type.StandingsGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraph;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraphMapper;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistration;
import com.dps.roboleague.results.port.in.GetStandings;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.TeamId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Resuelve la consulta raíz {@code standings} y el equipo de cada fila de la tabla. */
@Controller
public final class StandingsGraphController {

    private final GetStandings getStandings;
    private final FindTeamRegistration findTeamRegistration;

    public StandingsGraphController(GetStandings getStandings, FindTeamRegistration findTeamRegistration) {
        this.getStandings = getStandings;
        this.findTeamRegistration = findTeamRegistration;
    }

    @QueryMapping
    public StandingsGraph standings(@Argument("competitionId") String competitionId,
            @Argument("categoryId") String categoryId) {
        return StandingsGraphMapper.toGraph(getStandings.execute(
                new GetStandings.Command(CompetitionId.of(competitionId), CategoryId.of(categoryId))).latest());
    }

    /** Un equipo por fila: es el N+1 que la etapa siguiente resuelve con carga por lotes. */
    @SchemaMapping(typeName = "StandingEntry", field = "team")
    public TeamGraph team(StandingsGraph.EntryGraph entry) {
        return TeamGraphMapper.toGraph(findTeamRegistration.execute(TeamId.of(entry.teamId())));
    }
}
