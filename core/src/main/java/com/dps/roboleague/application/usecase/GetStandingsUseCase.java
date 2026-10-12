package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.ranking.StandingsRepository;
import com.dps.roboleague.domain.shared.NotFoundException;

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
