package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import java.util.List;

public interface GetStandings {

    Result execute(Command command);

    record Command(CompetitionId competitionId, CategoryId categoryId) {
    }

    record Result(Standings latest, List<Standings> history) {
    }
}
