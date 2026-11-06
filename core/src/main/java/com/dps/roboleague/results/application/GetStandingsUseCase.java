package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.GetStandings;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.shared.NotFoundException;

public final class GetStandingsUseCase implements GetStandings {

    private final StandingsRepository standings;

    public GetStandingsUseCase(StandingsRepository standings) {
        this.standings = standings;
    }

    @Override
    public Result execute(Command command) {
        Standings latest = standings.findLatest(command.competitionId(), command.categoryId())
                .orElseThrow(() -> NotFoundException.of("Standings", command.categoryId().value()));
        return new Result(latest, standings.findHistory(command.competitionId(), command.categoryId()));
    }
}
