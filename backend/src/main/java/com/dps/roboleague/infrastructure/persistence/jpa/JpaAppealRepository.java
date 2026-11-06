package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.appeals.Appeal;
import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.RunId;
import java.util.List;
import java.util.Optional;

public final class JpaAppealRepository implements AppealRepository {

    private final AppealRows rows;
    private final AggregateJson json;

    public JpaAppealRepository(AppealRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void save(Appeal appeal) {
        rows.save(new AppealRow(appeal.id().value(), appeal.runId().value(), json.write(appeal)));
    }

    @Override
    public Optional<Appeal> findById(AppealId id) {
        return rows.findById(id.value()).map(row -> json.read(row.payload(), Appeal.class));
    }

    @Override
    public List<Appeal> findByRun(RunId runId) {
        return rows.findByRunIdOrderById(runId.value()).stream()
                .map(row -> json.read(row.payload(), Appeal.class))
                .toList();
    }
}
