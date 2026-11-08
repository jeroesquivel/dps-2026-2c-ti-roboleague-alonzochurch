package com.dps.roboleague.adapter.in.graphql.competition;

import com.dps.roboleague.enrollment.port.in.FindCompetition;
import com.dps.roboleague.shared.CompetitionId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Resuelve la consulta raíz {@code competition(id)} delegando en el puerto {@link FindCompetition}. */
@Controller
public final class CompetitionGraphController {

    private final FindCompetition findCompetition;

    public CompetitionGraphController(FindCompetition findCompetition) {
        this.findCompetition = findCompetition;
    }

    @QueryMapping
    public CompetitionGraph competition(@Argument("id") String id) {
        return CompetitionGraphMapper.toGraph(findCompetition.execute(CompetitionId.of(id)));
    }
}
