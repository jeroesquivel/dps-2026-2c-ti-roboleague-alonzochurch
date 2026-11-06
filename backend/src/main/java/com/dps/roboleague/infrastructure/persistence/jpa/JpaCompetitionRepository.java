package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.shared.CompetitionId;
import java.util.Optional;

public final class JpaCompetitionRepository implements CompetitionRepository {

    private final CompetitionRows rows;
    private final AggregateJson json;

    public JpaCompetitionRepository(CompetitionRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void save(Competition competition) {
        rows.save(new CompetitionRow(competition.id().value(), competition.seasonId().value(),
                json.write(competition)));
    }

    @Override
    public Optional<Competition> findById(CompetitionId id) {
        return rows.findById(id.value()).map(row -> json.read(row.payload(), Competition.class));
    }
}
