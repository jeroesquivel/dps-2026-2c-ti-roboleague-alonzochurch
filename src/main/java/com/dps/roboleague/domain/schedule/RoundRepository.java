package com.dps.roboleague.domain.schedule;

import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.RoundId;
import java.util.List;
import java.util.Optional;

public interface RoundRepository {

    void save(Round round);

    Optional<Round> findById(RoundId id);

    List<Round> findByCompetition(CompetitionId competitionId);

    List<Round> findByCategory(CompetitionId competitionId, CategoryId categoryId);
}
