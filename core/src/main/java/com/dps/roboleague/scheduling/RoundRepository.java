package com.dps.roboleague.scheduling;

import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.RoundId;
import java.util.List;
import java.util.Optional;

public interface RoundRepository {

    void save(Round round);

    Optional<Round> findById(RoundId id);

    List<Round> findByCompetition(CompetitionId competitionId);

    List<Round> findByCategory(CompetitionId competitionId, CategoryId categoryId);
}
