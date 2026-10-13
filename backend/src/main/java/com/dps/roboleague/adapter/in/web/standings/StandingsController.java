package com.dps.roboleague.adapter.in.web.standings;

import com.dps.roboleague.domain.port.in.FindTeamRegistration;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.TeamId;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/competitions/{competitionId}/categories/{categoryId}/standings")
public final class StandingsController {

    private final GenerateStandings generateStandings;
    private final GetStandings getStandings;
    private final PublishStandings publishStandings;
    private final RecalculateStandings recalculateStandings;
    private final FindTeamRegistration findTeamRegistration;

    public StandingsController(GenerateStandings generateStandings, GetStandings getStandings,
            PublishStandings publishStandings, RecalculateStandings recalculateStandings,
            FindTeamRegistration findTeamRegistration) {
        this.generateStandings = generateStandings;
        this.getStandings = getStandings;
        this.publishStandings = publishStandings;
        this.recalculateStandings = recalculateStandings;
        this.findTeamRegistration = findTeamRegistration;
    }

    @PostMapping
    public ResponseEntity<StandingsResponse> generate(@PathVariable("competitionId") String competitionId,
            @PathVariable("categoryId") String categoryId, @RequestBody StandingsActionRequest request) {
        Standings generated = generateStandings.execute(
                StandingsWebMapper.toGenerateCommand(competitionId, categoryId, request));
        return created(competitionId, categoryId, generated);
    }

    @GetMapping
    public StandingsResponse latest(@PathVariable("competitionId") String competitionId,
            @PathVariable("categoryId") String categoryId) {
        return StandingsWebMapper.toResponse(find(competitionId, categoryId).latest(), teamNames());
    }

    @GetMapping("/revisions")
    public RevisionsResponse revisions(@PathVariable("competitionId") String competitionId,
            @PathVariable("categoryId") String categoryId) {
        return StandingsWebMapper.toResponse(find(competitionId, categoryId).history(), teamNames());
    }

    @PostMapping("/publication")
    public StandingsResponse publish(@PathVariable("competitionId") String competitionId,
            @PathVariable("categoryId") String categoryId, @RequestBody StandingsActionRequest request) {
        return StandingsWebMapper.toResponse(publishStandings.execute(
                StandingsWebMapper.toPublishCommand(competitionId, categoryId, request)), teamNames());
    }

    @PostMapping("/recalculation")
    public ResponseEntity<StandingsResponse> recalculate(@PathVariable("competitionId") String competitionId,
            @PathVariable("categoryId") String categoryId, @RequestBody RecalculateStandingsRequest request) {
        Standings recalculated = recalculateStandings.execute(
                StandingsWebMapper.toRecalculateCommand(competitionId, categoryId, request));
        return created(competitionId, categoryId, recalculated);
    }

    private GetStandings.Result find(String competitionId, String categoryId) {
        return getStandings.execute(new GetStandings.Command(CompetitionId.of(competitionId),
                CategoryId.of(categoryId)));
    }

    private ResponseEntity<StandingsResponse> created(String competitionId, String categoryId, Standings standings) {
        return ResponseEntity
                .created(URI.create("/competitions/" + competitionId + "/categories/" + categoryId + "/standings"))
                .body(StandingsWebMapper.toResponse(standings, teamNames()));
    }

    /** Nombre legible de cada equipo, consultado una vez por petición mediante el puerto de inscripciones. */
    private Function<TeamId, String> teamNames() {
        Map<TeamId, String> known = new HashMap<>();
        return teamId -> known.computeIfAbsent(teamId, id -> findTeamRegistration.execute(id).name());
    }
}
