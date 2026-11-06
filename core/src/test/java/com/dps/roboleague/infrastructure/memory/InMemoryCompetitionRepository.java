package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.shared.CompetitionId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryCompetitionRepository implements CompetitionRepository {

    private final Map<CompetitionId, Competition> competitions = new HashMap<>();

    @Override
    public void save(Competition competition) {
        competitions.put(competition.id(), competition);
    }

    @Override
    public Optional<Competition> findById(CompetitionId id) {
        return Optional.ofNullable(competitions.get(id));
    }
}
