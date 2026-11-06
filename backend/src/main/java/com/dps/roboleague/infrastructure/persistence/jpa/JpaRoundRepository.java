package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.RoundId;
import java.util.List;
import java.util.Optional;

public final class JpaRoundRepository implements RoundRepository {

    private final RoundRows rows;
    private final AggregateJson json;

    public JpaRoundRepository(RoundRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void save(Round round) {
        rows.save(new RoundRow(round.id().value(), round.competitionId().value(), round.categoryId().value(),
                round.ordinal().value(), json.write(round)));
    }

    @Override
    public Optional<Round> findById(RoundId id) {
        return rows.findById(id.value()).map(row -> json.read(row.payload(), Round.class));
    }

    @Override
    public List<Round> findByCompetition(CompetitionId competitionId) {
        return readAll(rows.findByCompetitionIdOrderByOrdinalAsc(competitionId.value()));
    }

    @Override
    public List<Round> findByCategory(CompetitionId competitionId, CategoryId categoryId) {
        return readAll(rows.findByCompetitionIdAndCategoryIdOrderByOrdinalAsc(competitionId.value(),
                categoryId.value()));
    }

    private List<Round> readAll(List<RoundRow> found) {
        return found.stream().map(row -> json.read(row.payload(), Round.class)).toList();
    }
}
