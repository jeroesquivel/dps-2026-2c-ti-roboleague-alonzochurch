package com.dps.roboleague.adapter.in.graphql.team;

import com.dps.roboleague.adapter.in.graphql.type.CategoryGraph;
import com.dps.roboleague.adapter.in.graphql.type.CompetitionGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.RoundGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.RoundResultGraph;
import com.dps.roboleague.adapter.in.graphql.type.RunGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraph;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraphMapper;
import com.dps.roboleague.enrollment.port.in.FindCompetition;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistration;
import com.dps.roboleague.results.port.in.FindTeamRuns;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.scheduling.port.in.FindRound;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.TeamId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Consulta un equipo y agrupa sus corridas por ronda sin trasladar reglas de puntaje al adaptador. */
@Controller
public final class TeamGraphController {

    private final FindTeamRegistration findTeam;
    private final FindTeamRuns findRuns;
    private final FindRound findRound;
    private final FindCompetition findCompetition;

    public TeamGraphController(FindTeamRegistration findTeam, FindTeamRuns findRuns, FindRound findRound,
            FindCompetition findCompetition) {
        this.findTeam = findTeam;
        this.findRuns = findRuns;
        this.findRound = findRound;
        this.findCompetition = findCompetition;
    }

    @QueryMapping
    public TeamGraph team(@Argument("id") String id) {
        return TeamGraphMapper.toGraph(findTeam.execute(TeamId.of(id)));
    }

    @SchemaMapping(typeName = "Team", field = "category")
    public CategoryGraph category(TeamGraph team) {
        CategoryId categoryId = CategoryId.of(team.categoryId());
        return CompetitionGraphMapper.toGraph(findCompetition.execute(CompetitionId.of(team.competitionId())))
                .categories().stream().filter(category -> category.id().equals(categoryId.value())).findFirst()
                .orElseThrow(() -> new NotFoundException("category " + categoryId.value() + " was not found"));
    }

    @SchemaMapping(typeName = "Team", field = "results")
    public List<RoundResultGraph> results(TeamGraph team) {
        Map<com.dps.roboleague.shared.RoundId, List<RunResult>> byRound = findRuns
                .execute(new FindTeamRuns.Command(TeamId.of(team.id()))).stream()
                .collect(Collectors.groupingBy(RunResult::roundId));
        return byRound.entrySet().stream()
                .map(entry -> new RoundResultGraph(RoundGraphMapper.toGraph(findRound.execute(entry.getKey())),
                        entry.getValue().stream().sorted(Comparator.comparing(RunResult::attemptNumber))
                                .map(RunGraphMapper::toGraph).toList()))
                .sorted(Comparator.comparing(result -> result.round().ordinal()))
                .toList();
    }
}
