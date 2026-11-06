package com.dps.roboleague.enrollment.competition;

import com.dps.roboleague.shared.SeasonId;
import java.util.List;
import java.util.Optional;

public interface SeasonRepository {

    void save(Season season);

    Optional<Season> findById(SeasonId id);

    List<Season> findAll();
}
