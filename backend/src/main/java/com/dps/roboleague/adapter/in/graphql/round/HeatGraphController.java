package com.dps.roboleague.adapter.in.graphql.round;

import com.dps.roboleague.adapter.in.graphql.type.RoundGraph;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraph;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Resuelve el equipo de cada turno de una ronda. */
@Controller
public final class HeatGraphController {

    /** No pide el equipo: lo encola en el {@code DataLoader}, que carga los de todos los turnos en un solo lote. */
    @SchemaMapping(typeName = "Heat", field = "team")
    public CompletableFuture<TeamGraph> team(RoundGraph.HeatGraph heat, DataLoader<String, TeamGraph> teams) {
        return teams.load(heat.teamId());
    }
}
