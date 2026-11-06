package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import java.util.List;
import java.util.Optional;

/** Mismas reglas que la versión en memoria: un intento por equipo y ronda, y no se pisa una corrida desactualizada. */
public final class JpaRunResultRepository implements RunResultRepository {

    private final RunResultRows rows;
    private final AggregateJson json;

    public JpaRunResultRepository(RunResultRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void save(RunResult result) {
        String id = result.id().value();
        boolean attemptTaken = rows.existsByRoundIdAndTeamIdAndAttemptNumberAndIdNot(result.roundId().value(),
                result.teamId().value(), result.attemptNumber().value(), id);
        if (attemptTaken) {
            throw new ConflictException("attempt " + result.attemptNumber() + " of team " + result.teamId().value()
                    + " is already stored for round " + result.roundId().value());
        }
        Optional<RunResult> stored = rows.findById(id).map(row -> json.read(row.payload(), RunResult.class));
        if (stored.isPresent() && !result.completion().keepsReceiptsOf(stored.get().completion())) {
            throw new ConflictException("run " + id
                    + " changed since it was read: saving it would drop a registered source");
        }
        rows.save(new RunResultRow(id, result.roundId().value(), result.teamId().value(),
                result.attemptNumber().value(), json.write(result)));
    }

    @Override
    public Optional<RunResult> findById(RunId id) {
        return rows.findById(id.value()).map(row -> json.read(row.payload(), RunResult.class));
    }

    @Override
    public List<RunResult> findByRound(RoundId roundId) {
        return rows.findByRoundIdOrderByAttemptNumberAsc(roundId.value()).stream()
                .map(row -> json.read(row.payload(), RunResult.class))
                .toList();
    }
}
