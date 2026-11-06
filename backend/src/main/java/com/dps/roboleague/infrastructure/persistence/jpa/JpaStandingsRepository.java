package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import java.util.List;
import java.util.Optional;

public final class JpaStandingsRepository implements StandingsRepository {

    private final StandingsRows rows;
    private final AggregateJson json;

    public JpaStandingsRepository(StandingsRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void save(Standings standings) {
        String competition = standings.competitionId().value();
        String category = standings.categoryId().value();
        int revision = standings.revision().number();
        rows.save(new StandingsRow(Rows.standingsKey(competition, category, revision), competition, category,
                revision, json.write(standings)));
    }

    @Override
    public Optional<Standings> findLatest(CompetitionId competitionId, CategoryId categoryId) {
        return rows.findFirstByCompetitionIdAndCategoryIdOrderByRevisionDesc(competitionId.value(),
                categoryId.value()).map(row -> json.read(row.payload(), Standings.class));
    }

    @Override
    public List<Standings> findHistory(CompetitionId competitionId, CategoryId categoryId) {
        return rows.findByCompetitionIdAndCategoryIdOrderByRevisionAsc(competitionId.value(), categoryId.value())
                .stream()
                .map(row -> json.read(row.payload(), Standings.class))
                .toList();
    }
}
