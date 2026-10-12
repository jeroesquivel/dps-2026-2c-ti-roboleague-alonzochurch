package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryRunResultRepository implements RunResultRepository {

    private final Map<RunId, RunResult> results = new LinkedHashMap<>();

    @Override
    public synchronized void save(RunResult result) {
        boolean attemptTaken = results.values().stream()
                .anyMatch(stored -> !stored.id().equals(result.id())
                        && stored.roundId().equals(result.roundId())
                        && stored.teamId().equals(result.teamId())
                        && stored.attemptNumber().equals(result.attemptNumber()));
        if (attemptTaken) {
            throw new ConflictException("attempt " + result.attemptNumber() + " of team " + result.teamId().value()
                    + " is already stored for round " + result.roundId().value());
        }
        RunResult stored = results.get(result.id());
        if (stored != null && !result.completion().keepsReceiptsOf(stored.completion())) {
            throw new ConflictException("run " + result.id().value()
                    + " changed since it was read: saving it would drop a registered source");
        }
        results.put(result.id(), result);
    }

    @Override
    public synchronized Optional<RunResult> findById(RunId id) {
        return Optional.ofNullable(results.get(id));
    }

    @Override
    public synchronized List<RunResult> findByRound(RoundId roundId) {
        return results.values().stream()
                .filter(result -> result.roundId().equals(roundId))
                .sorted(Comparator.comparing(RunResult::attemptNumber))
                .toList();
    }
}
