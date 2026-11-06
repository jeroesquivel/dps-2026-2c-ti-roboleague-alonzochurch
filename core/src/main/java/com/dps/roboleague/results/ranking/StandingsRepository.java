package com.dps.roboleague.results.ranking;

import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import java.util.List;
import java.util.Optional;

public interface StandingsRepository {

    void save(Standings standings);

    Optional<Standings> findLatest(CompetitionId competitionId, CategoryId categoryId);

    List<Standings> findHistory(CompetitionId competitionId, CategoryId categoryId);
}
