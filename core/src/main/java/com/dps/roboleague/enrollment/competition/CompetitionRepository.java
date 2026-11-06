package com.dps.roboleague.enrollment.competition;

import com.dps.roboleague.shared.CompetitionId;
import java.util.Optional;

public interface CompetitionRepository {

    void save(Competition competition);

    Optional<Competition> findById(CompetitionId id);
}
