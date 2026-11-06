package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.enrollment.competition.Season;
import com.dps.roboleague.enrollment.competition.SeasonRepository;
import com.dps.roboleague.shared.SeasonId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySeasonRepository implements SeasonRepository {

    private final Map<SeasonId, Season> seasons = new LinkedHashMap<>();

    @Override
    public void save(Season season) {
        seasons.put(season.id(), season);
    }

    @Override
    public Optional<Season> findById(SeasonId id) {
        return Optional.ofNullable(seasons.get(id));
    }

    @Override
    public List<Season> findAll() {
        return List.copyOf(seasons.values());
    }
}
