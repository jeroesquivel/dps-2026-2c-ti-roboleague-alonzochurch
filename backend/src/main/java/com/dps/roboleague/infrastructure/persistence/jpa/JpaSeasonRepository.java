package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.enrollment.competition.Season;
import com.dps.roboleague.enrollment.competition.SeasonRepository;
import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.shared.SeasonId;
import java.util.List;
import java.util.Optional;

public final class JpaSeasonRepository implements SeasonRepository {

    private final SeasonRows rows;
    private final AggregateJson json;

    public JpaSeasonRepository(SeasonRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void save(Season season) {
        String id = season.id().value();
        long order = rows.findById(id).map(SeasonRow::sortOrder).orElseGet(rows::count);
        rows.save(new SeasonRow(id, order, json.write(season)));
    }

    @Override
    public Optional<Season> findById(SeasonId id) {
        return rows.findById(id.value()).map(row -> json.read(row.payload(), Season.class));
    }

    @Override
    public List<Season> findAll() {
        return rows.findAllByOrderBySortOrderAsc().stream()
                .map(row -> json.read(row.payload(), Season.class))
                .toList();
    }
}
