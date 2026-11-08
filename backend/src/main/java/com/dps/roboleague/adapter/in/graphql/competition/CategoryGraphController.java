package com.dps.roboleague.adapter.in.graphql.competition;

import com.dps.roboleague.adapter.in.graphql.type.CategoryGraph;
import com.dps.roboleague.adapter.in.graphql.type.RoundGraph;
import com.dps.roboleague.adapter.in.graphql.type.RoundGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.StandingsGraph;
import com.dps.roboleague.adapter.in.graphql.type.StandingsGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraph;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraphMapper;
import com.dps.roboleague.enrollment.port.in.FindCategoryTeams;
import com.dps.roboleague.results.port.in.GetStandings;
import com.dps.roboleague.scheduling.port.in.FindCategoryRounds;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import java.util.List;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * Resuelve los campos de {@code Category} que se cargan aparte: sus equipos, sus rondas y su tabla de posiciones.
 * Cada campo se pide sólo si la consulta lo incluye, y cada uno delega en su puerto de entrada.
 */
@Controller
public final class CategoryGraphController {

    private final FindCategoryTeams findCategoryTeams;
    private final FindCategoryRounds findCategoryRounds;
    private final GetStandings getStandings;

    public CategoryGraphController(FindCategoryTeams findCategoryTeams, FindCategoryRounds findCategoryRounds,
            GetStandings getStandings) {
        this.findCategoryTeams = findCategoryTeams;
        this.findCategoryRounds = findCategoryRounds;
        this.getStandings = getStandings;
    }

    @SchemaMapping(typeName = "Category", field = "teams")
    public List<TeamGraph> teams(CategoryGraph category) {
        return findCategoryTeams.execute(new FindCategoryTeams.Command(competitionOf(category), idOf(category)))
                .stream().map(TeamGraphMapper::toGraph).toList();
    }

    @SchemaMapping(typeName = "Category", field = "rounds")
    public List<RoundGraph> rounds(CategoryGraph category) {
        return findCategoryRounds.execute(new FindCategoryRounds.Command(competitionOf(category), idOf(category)))
                .stream().map(RoundGraphMapper::toGraph).toList();
    }

    @SchemaMapping(typeName = "Category", field = "standings")
    public StandingsGraph standings(CategoryGraph category) {
        return StandingsGraphMapper.toGraph(getStandings.execute(
                new GetStandings.Command(competitionOf(category), idOf(category))).latest());
    }

    private static CompetitionId competitionOf(CategoryGraph category) {
        return CompetitionId.of(category.competitionId());
    }

    private static CategoryId idOf(CategoryGraph category) {
        return CategoryId.of(category.id());
    }
}
