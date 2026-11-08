package com.dps.roboleague.adapter.in.graphql.standings;

import com.dps.roboleague.adapter.in.graphql.type.StandingsGraph;
import com.dps.roboleague.adapter.in.graphql.type.StandingsGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraph;
import com.dps.roboleague.results.port.in.GetStandings;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Resuelve la consulta raíz {@code standings} y el equipo de cada fila de la tabla. */
@Controller
public final class StandingsGraphController {

    private final GetStandings getStandings;

    public StandingsGraphController(GetStandings getStandings) {
        this.getStandings = getStandings;
    }

    @QueryMapping
    public StandingsGraph standings(@Argument("competitionId") String competitionId,
            @Argument("categoryId") String categoryId) {
        return StandingsGraphMapper.toGraph(getStandings.execute(
                new GetStandings.Command(CompetitionId.of(competitionId), CategoryId.of(categoryId))).latest());
    }

    /** No pide el equipo: lo encola en el {@code DataLoader}, que carga los de todas las filas en un solo lote. */
    @SchemaMapping(typeName = "StandingEntry", field = "team")
    public CompletableFuture<TeamGraph> team(StandingsGraph.EntryGraph entry, DataLoader<String, TeamGraph> teams) {
        return teams.load(entry.teamId());
    }
}
