package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;

public interface GenerateStandings {

    Standings execute(Command command);

    record Command(CompetitionId competitionId, CategoryId categoryId, Actor actor) {
    }
}
