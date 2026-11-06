package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;

public interface GenerateStandings {

    Standings execute(Command command);

    record Command(CompetitionId competitionId, CategoryId categoryId, Actor actor) {
    }
}
