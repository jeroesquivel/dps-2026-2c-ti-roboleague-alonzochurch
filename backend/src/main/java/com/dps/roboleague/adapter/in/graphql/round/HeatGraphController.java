package com.dps.roboleague.adapter.in.graphql.round;

import com.dps.roboleague.adapter.in.graphql.type.RoundGraph;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraph;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraphMapper;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistration;
import com.dps.roboleague.shared.TeamId;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Resuelve el equipo de cada turno de una ronda. */
@Controller
public final class HeatGraphController {

    private final FindTeamRegistration findTeamRegistration;

    public HeatGraphController(FindTeamRegistration findTeamRegistration) {
        this.findTeamRegistration = findTeamRegistration;
    }

    /** Un equipo por turno: es el N+1 que la etapa siguiente resuelve con carga por lotes. */
    @SchemaMapping(typeName = "Heat", field = "team")
    public TeamGraph team(RoundGraph.HeatGraph heat) {
        return TeamGraphMapper.toGraph(findTeamRegistration.execute(TeamId.of(heat.teamId())));
    }
}
